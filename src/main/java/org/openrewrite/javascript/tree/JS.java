/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.javascript.tree;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.internal.TypesInUse;
import org.openrewrite.java.tree.*;
import org.openrewrite.javascript.JavaScriptVisitor;
import org.openrewrite.javascript.internal.JavaScriptPrinter;
import org.openrewrite.marker.Markers;

import java.beans.Transient;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public interface JS extends J {

    @Override
    default <R extends Tree, P> R accept(TreeVisitor<R, P> v, P p) {
        //noinspection unchecked
        return (R) acceptJavaScript(v.adapt(JavaScriptVisitor.class), p);
    }

    @Override
    default <P> boolean isAcceptable(TreeVisitor<?, P> v, P p) {
        return v.isAdaptableTo(JavaScriptVisitor.class);
    }

    @Nullable
    default <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
        return v.defaultValue(this, p);
    }

    Space getPrefix();

    default List<Comment> getComments() {
        return getPrefix().getComments();
    }

    @ToString
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class CompilationUnit implements JS, JavaSourceFile, SourceFile {
        @Nullable
        @NonFinal
        transient SoftReference<TypesInUse> typesInUse;

        @Nullable
        @NonFinal
        transient WeakReference<Padding> padding;

        @EqualsAndHashCode.Include
        @With
        @Getter
        UUID id;

        @With
        @Getter
        Space prefix;

        @With
        @Getter
        Markers markers;

        @With
        @Getter
        Path sourcePath;

        @With
        @Getter
        @Nullable
        FileAttributes fileAttributes;

        @Nullable // for backwards compatibility
        @With(AccessLevel.PRIVATE)
        String charsetName;

        @With
        @Getter
        boolean charsetBomMarked;

        @With
        @Getter
        @Nullable
        Checksum checksum;

        @Override
        public Charset getCharset() {
            return charsetName == null ? StandardCharsets.UTF_8 : Charset.forName(charsetName);
        }

        @SuppressWarnings("unchecked")
        @Override
        public SourceFile withCharset(Charset charset) {
            return withCharsetName(charset.name());
        }

        List<JRightPadded<Import>> imports;

        public List<Import> getImports() {
            return JRightPadded.getElements(imports);
        }

        public JS.CompilationUnit withImports(List<Import> imports) {
            return getPadding().withImports(JRightPadded.withElements(this.imports, imports));
        }

        List<JRightPadded<Statement>> statements;

        public List<Statement> getStatements() {
            return JRightPadded.getElements(statements);
        }

        public JS.CompilationUnit withStatements(List<Statement> statements) {
            return getPadding().withStatements(JRightPadded.withElements(this.statements, statements));
        }

        @With
        @Getter
        Space eof;

        @Transient
        public @NonNull List<ClassDeclaration> getClasses() {
            return statements.stream()
                    .map(JRightPadded::getElement)
                    .filter(J.ClassDeclaration.class::isInstance)
                    .map(J.ClassDeclaration.class::cast)
                    .collect(Collectors.toList());
        }

        @Override
        @NonNull
        public JavaSourceFile withClasses(List<ClassDeclaration> classes) {
            // FIXME unsupported
            return this;
        }

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitCompilationUnit(this, p);
        }

        @Override
        public <P> TreeVisitor<?, PrintOutputCapture<P>> printer(Cursor cursor) {
            return new JavaScriptPrinter<>();
        }

        @Transient
        @NonNull
        public TypesInUse getTypesInUse() {
            TypesInUse cache;
            if (this.typesInUse == null) {
                cache = TypesInUse.build(this);
                this.typesInUse = new SoftReference<>(cache);
            } else {
                cache = this.typesInUse.get();
                if (cache == null || cache.getCu() != this) {
                    cache = TypesInUse.build(this);
                    this.typesInUse = new SoftReference<>(cache);
                }
            }
            return cache;
        }

        @Override
        public @Nullable Package getPackageDeclaration() {
            return null;
        }

        @Override
        public JavaSourceFile withPackageDeclaration(Package pkg) {
            throw new IllegalStateException("JavaScript does not support package declarations");
        }

        public Padding getPadding() {
            Padding p;
            if (this.padding == null) {
                p = new Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding implements JavaSourceFile.Padding {
            private final JS.CompilationUnit t;

            @Override
            public List<JRightPadded<Import>> getImports() {
                return t.imports;
            }

            @Override
            public JS.CompilationUnit withImports(List<JRightPadded<Import>> imports) {
                return t.imports == imports ? t : new JS.CompilationUnit(t.id, t.prefix, t.markers, t.sourcePath, t.fileAttributes, t.charsetName, t.charsetBomMarked, null,
                        imports, t.statements, t.eof);
            }

            public List<JRightPadded<Statement>> getStatements() {
                return t.statements;
            }

            public JS.CompilationUnit withStatements(List<JRightPadded<Statement>> statements) {
                return t.statements == statements ? t : new JS.CompilationUnit(t.id, t.prefix, t.markers, t.sourcePath,
                        t.fileAttributes, t.charsetName, t.charsetBomMarked, t.checksum, t.imports, statements, t.eof);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Data
    final class Alias implements JS, Expression {

        @Nullable
        @NonFinal
        transient WeakReference<JS.Alias.Padding> padding;

        @With
        @EqualsAndHashCode.Include
        UUID id;

        @With
        Space prefix;

        @With
        Markers markers;

        JRightPadded<J.Identifier> propertyName;

        @With
        J.Identifier alias;

        public J.Identifier getPropertyName() {
            return propertyName.getElement();
        }

        public JS.Alias withPropertyName(J.Identifier propertyName) {
            return getPadding().withPropertyName(this.propertyName.withElement(propertyName));
        }

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitAlias(this, p);
        }

        @Nullable
        @Override
        public JavaType getType() {
            return propertyName.getElement().getType();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Alias withType(@Nullable JavaType type) {
            return withPropertyName(propertyName.getElement().withType(type));
        }

        @Transient
        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        public JS.Alias.Padding getPadding() {
            JS.Alias.Padding p;
            if (this.padding == null) {
                p = new JS.Alias.Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new JS.Alias.Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final JS.Alias t;

            public JRightPadded<J.Identifier> getPropertyName() {
                return t.propertyName;
            }

            public JS.Alias withPropertyName(JRightPadded<J.Identifier> propertyName) {
                return t.propertyName == propertyName ? t : new JS.Alias(t.id, t.prefix, t.markers, propertyName, t.alias);
            }
        }
    }

    /**
     * A JavaScript `=>` is similar to a Java lambda, but additionally contains annotations, modifiers, type arguments.
     * The ArrowFunction prevents J.Lambda recipes from transforming the LST because an ArrowFunction
     * may not be transformed in the same way as a J.Lambda.
     */
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    final class ArrowFunction implements JS, Statement, Expression, TypedTree {

        @With
        @EqualsAndHashCode.Include
        UUID id;

        @With
        Space prefix;

        @With
        Markers markers;

        @With
        List<J.Annotation> leadingAnnotations;

        @With
        List<J.Modifier> modifiers;

        @With
        Lambda.Parameters parameters;

        @With
        @Getter
        @Nullable
        TypeTree returnTypeExpression;

        @With
        Space arrow;

        @With
        J body;

        @With
        @Nullable
        JavaType type;

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitArrowFunction(this, p);
        }

        @Override
        @Transient
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    final class DefaultType implements JS, Expression, TypedTree, NameTree {

        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;
        Expression left;
        Space beforeEquals;
        Expression right;

        @Nullable
        JavaType type;

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitDefaultType(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }
    }

    @SuppressWarnings("unchecked")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @Data
    @With
    final class Delete implements JS, Expression, Statement {

        @EqualsAndHashCode.Include
        UUID id;

        Space prefix;
        Markers markers;
        Expression expression;

        @Nullable
        JavaType type;

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitDelete(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    class Export implements JS, Statement {

        @Nullable
        @NonFinal
        transient WeakReference<Export.Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        @Nullable
        JContainer<Expression> exports;

        @Nullable
        public List<Expression> getExports() {
            return exports == null ? null : exports.getElements();
        }

        public Export withExports(List<Expression> exports) {
            return getPadding().withExports(JContainer.withElementsNullable(this.exports, exports));
        }

        @Getter
        @With
        @Nullable
        Space from;

        @Getter
        @With
        @Nullable
        J.Literal target;

        @Nullable
        JLeftPadded<Expression> initializer;

        @Nullable
        public Expression getInitializer() {
            return initializer == null ? null : initializer.getElement();
        }

        public Export withInitializer(@Nullable Expression initializer) {
            return getPadding().withInitializer(JLeftPadded.withElement(this.initializer, initializer));
        }

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitExport(this, p);
        }

        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        public Export.Padding getPadding() {
            Export.Padding p;
            if (this.padding == null) {
                p = new Export.Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new Export.Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final Export t;

            @Nullable
            public JContainer<Expression> getExports() {
                return t.exports;
            }

            public Export withExports(@Nullable JContainer<Expression> exports) {
                return t.exports == exports ? t : new Export(t.id, t.prefix, t.markers, exports, t.from, t.target, t.initializer);
            }

            @Nullable
            public JLeftPadded<Expression> getInitializer() {
                return t.initializer;
            }

            public Export withInitializer(@Nullable JLeftPadded<Expression> initializer) {
                return t.initializer == initializer ? t : new Export(t.id, t.prefix, t.markers, t.exports, t.from, t.target, initializer);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @AllArgsConstructor
    final class ExpressionStatement implements JS, Expression, Statement {

        @With
        @Getter
        UUID id;

        @With
        @Getter
        Expression expression;

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            J j = v.visit(getExpression(), p);
            if (j instanceof ExpressionStatement) {
                return j;
            } else if (j instanceof Expression) {
                return withExpression((Expression) j);
            }
            return j;
        }

        @Override
        public <J2 extends J> J2 withPrefix(Space space) {
            return (J2) withExpression(expression.withPrefix(space));
        }

        @Override
        public Space getPrefix() {
            return expression.getPrefix();
        }

        @Override
        public <J2 extends Tree> J2 withMarkers(Markers markers) {
            return (J2) withExpression(expression.withMarkers(markers));
        }

        @Override
        public Markers getMarkers() {
            return expression.getMarkers();
        }

        @Nullable
        @Override
        public JavaType getType() {
            return expression.getType();
        }

        @Override
        public ExpressionStatement withType(@Nullable JavaType type) {
            return withExpression(expression.withType(type));
        }

        @Transient
        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    class FunctionType implements JS, Expression, TypeTree {

        @Nullable
        @NonFinal
        transient WeakReference<FunctionType.Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        JContainer<Statement> parameters;

        public List<Statement> getParameters() {
            return parameters.getElements();
        }

        public FunctionType withParameters(List<Statement> parameters) {
            return getPadding().withParameters(JContainer.withElements(this.parameters, parameters));
        }

        @Getter
        @With
        Space arrow;

        @Getter
        @With
        Expression returnType;

        @Getter
        @With
        @Nullable
        JavaType type;

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitFunctionType(this, p);
        }

        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        public FunctionType.Padding getPadding() {
            FunctionType.Padding p;
            if (this.padding == null) {
                p = new FunctionType.Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new FunctionType.Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final FunctionType t;

            public JContainer<Statement> getParameters() {
                return t.parameters;
            }

            public FunctionType withParameters(JContainer<Statement> parameters) {
                return t.parameters == parameters ? t : new FunctionType(t.id, t.prefix, t.markers, parameters, t.arrow, t.returnType, t.type);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    class JsImport implements JS, Statement {

        @Nullable
        @NonFinal
        transient WeakReference<JsImport.Padding> padding;

        @Getter
        @With
        @EqualsAndHashCode.Include
        UUID id;

        @Getter
        @With
        Space prefix;

        @Getter
        @With
        Markers markers;

        @Nullable
        JRightPadded<J.Identifier> name;

        @Nullable
        public J.Identifier getName() {
            return name == null ? null : name.getElement();
        }

        public JsImport withName(@Nullable J.Identifier name) {
            return getPadding().withName(JRightPadded.withElement(this.name, name));
        }

        @Nullable
        JContainer<Expression> imports;

        @Nullable
        public List<Expression> getImports() {
            return imports == null ? null : imports.getElements();
        }

        public JsImport withImports(List<Expression> imports) {
            return getPadding().withImports(JContainer.withElementsNullable(this.imports, imports));
        }

        @Nullable
        @Getter
        @With
        Space from;

        @Nullable
        @Getter
        @With
        J.Literal target;

        @Nullable
        JLeftPadded<Expression> initializer;

        @Nullable
        public Expression getInitializer() {
            return initializer == null ? null : initializer.getElement();
        }

        public JS.JsImport withInitializer(@Nullable Expression initializer) {
            return getPadding().withInitializer(JLeftPadded.withElement(this.initializer, initializer));
        }

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitJsImport(this, p);
        }

        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        public JsImport.Padding getPadding() {
            JsImport.Padding p;
            if (this.padding == null) {
                p = new JsImport.Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new JsImport.Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final JsImport t;

            @Nullable
            public JRightPadded<J.Identifier> getName() {
                return t.name;
            }

            public JsImport withName(@Nullable JRightPadded<J.Identifier> name) {
                return t.name == name ? t : new JsImport(t.id, t.prefix, t.markers, name, t.imports, t.from, t.target, t.initializer);
            }

            @Nullable
            public JContainer<Expression> getImports() {
                return t.imports;
            }

            public JsImport withImports(@Nullable JContainer<Expression> imports) {
                return t.imports == imports ? t : new JsImport(t.id, t.prefix, t.markers, t.name, imports, t.from, t.target, t.initializer);
            }

            @Nullable
            public JLeftPadded<Expression> getInitializer() {
                return t.initializer;
            }

            public JsImport withInitializer(@Nullable JLeftPadded<Expression> initializer) {
                return t.initializer == initializer ? t : new JsImport(t.id, t.prefix, t.markers, t.name, t.imports, t.from, t.target, initializer);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Data
    final class JsBinary implements JS, Expression, TypedTree {

        @Nullable
        @NonFinal
        transient WeakReference<JS.JsBinary.Padding> padding;

        @With
        @EqualsAndHashCode.Include
        UUID id;

        @With
        Space prefix;

        @With
        Markers markers;

        @With
        Expression left;

        JLeftPadded<JS.JsBinary.Type> operator;

        public JS.JsBinary.Type getOperator() {
            return operator.getElement();
        }

        public JS.JsBinary withOperator(JS.JsBinary.Type operator) {
            return getPadding().withOperator(this.operator.withElement(operator));
        }

        @With
        Expression right;

        @With
        @Nullable
        JavaType type;

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitJsBinary(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        public enum Type {
            IdentityEquals,
            IdentityNotEquals
        }

        public JS.JsBinary.Padding getPadding() {
            JS.JsBinary.Padding p;
            if (this.padding == null) {
                p = new JS.JsBinary.Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new JS.JsBinary.Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final JS.JsBinary t;

            public JLeftPadded<JS.JsBinary.Type> getOperator() {
                return t.operator;
            }

            public JS.JsBinary withOperator(JLeftPadded<JS.JsBinary.Type> operator) {
                return t.operator == operator ? t : new JS.JsBinary(t.id, t.prefix, t.markers, t.left, operator, t.right, t.type);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Data
    final class JsOperator implements JS, Statement, Expression, TypedTree, NameTree {

        @Nullable
        @NonFinal
        transient WeakReference<JS.JsOperator.Padding> padding;

        @With
        @EqualsAndHashCode.Include
        UUID id;

        @With
        Space prefix;

        @With
        Markers markers;

        @Nullable
        @With
        Expression left;

        JLeftPadded<JS.JsOperator.Type> operator;

        public JS.JsOperator.Type getOperator() {
            return operator.getElement();
        }

        public JS.JsOperator withOperator(JS.JsOperator.Type operator) {
            return getPadding().withOperator(this.operator.withElement(operator));
        }

        @With
        Expression right;

        @With
        @Nullable
        JavaType type;

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitJsOperator(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        public enum Type {
            Await,
            Delete,
            In,
            TypeOf
        }

        public JS.JsOperator.Padding getPadding() {
            JS.JsOperator.Padding p;
            if (this.padding == null) {
                p = new JS.JsOperator.Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new JS.JsOperator.Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final JS.JsOperator t;

            public JLeftPadded<JS.JsOperator.Type> getOperator() {
                return t.operator;
            }

            public JS.JsOperator withOperator(JLeftPadded<JS.JsOperator.Type> operator) {
                return t.operator == operator ? t : new JS.JsOperator(t.id, t.prefix, t.markers, t.left, operator, t.right, t.type);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class ObjectBindingDeclarations implements JS, Statement, TypedTree {

        @Nullable
        @NonFinal
        transient WeakReference<ObjectBindingDeclarations.Padding> padding;

        @With
        @EqualsAndHashCode.Include
        @Getter
        UUID id;

        @With
        @Getter
        Space prefix;

        @With
        @Getter
        Markers markers;

        @With
        @Getter
        List<J.Annotation> leadingAnnotations;

        @With
        @Getter
        List<J.Modifier> modifiers;

        @With
        @Nullable
        @Getter
        TypeTree typeExpression;

        JContainer<ObjectBindingDeclarations.Binding> bindings;

        public List<ObjectBindingDeclarations.Binding> getBindings() {
            return bindings.getElements();
        }

        public ObjectBindingDeclarations withBindings(List<ObjectBindingDeclarations.Binding> bindings) {
            return getPadding().withBindings(JContainer.withElements(this.bindings, bindings));
        }

        @Nullable
        JLeftPadded<Expression> initializer;

        @Nullable
        public Expression getInitializer() {
            return initializer == null ? null : initializer.getElement();
        }

        public ObjectBindingDeclarations withInitializer(@Nullable Expression initializer) {
            return getPadding().withInitializer(JLeftPadded.withElement(this.initializer, initializer));
        }

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitObjectBindingDeclarations(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        // gather annotations from everywhere they may occur
        public List<J.Annotation> getAllAnnotations() {
            List<Annotation> allAnnotations = new ArrayList<>(leadingAnnotations);
            for (J.Modifier modifier : modifiers) {
                allAnnotations.addAll(modifier.getAnnotations());
            }
            if (typeExpression != null && typeExpression instanceof J.AnnotatedType) {
                allAnnotations.addAll(((J.AnnotatedType) typeExpression).getAnnotations());
            }
            return allAnnotations;
        }

        @Nullable
        public JavaType.FullyQualified getTypeAsFullyQualified() {
            return typeExpression == null ? null : TypeUtils.asFullyQualified(typeExpression.getType());
        }

        @Nullable
        @Override
        public JavaType getType() {
            return typeExpression == null ? null : typeExpression.getType();
        }

        @SuppressWarnings("unchecked")
        @Override
        public ObjectBindingDeclarations withType(@Nullable JavaType type) {
            return typeExpression == null ? this :
                    withTypeExpression(typeExpression.withType(type));
        }

        @SuppressWarnings("unchecked")
        @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
        @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
        @RequiredArgsConstructor
        @AllArgsConstructor(access = AccessLevel.PRIVATE)
        public static final class Binding implements JS, NameTree {

            @Nullable
            @NonFinal
            transient WeakReference<ObjectBindingDeclarations.Binding.Padding> padding;

            @With
            @EqualsAndHashCode.Include
            @Getter
            UUID id;

            @With
            @Getter
            Space prefix;

            @With
            @Getter
            Markers markers;

            @Nullable
            JRightPadded<J.Identifier> propertyName;

            @Nullable
            public J.Identifier getPropertyName() {
                return propertyName == null ? null : propertyName.getElement();
            }

            public ObjectBindingDeclarations.Binding withPropertyName(@Nullable J.Identifier propertyName) {
                return getPadding().withPropertyName(JRightPadded.withElement(this.propertyName, propertyName));
            }

            @With
            @Getter
            Identifier name;

            @With
            @Getter
            List<JLeftPadded<Space>> dimensionsAfterName;

            @With
            @Nullable
            @Getter
            Space afterVararg;

            @Nullable
            JLeftPadded<Expression> initializer;

            @Nullable
            public Expression getInitializer() {
                return initializer == null ? null : initializer.getElement();
            }

            public ObjectBindingDeclarations.Binding withInitializer(@Nullable Expression initializer) {
                return getPadding().withInitializer(JLeftPadded.withElement(this.initializer, initializer));
            }

            @With
            @Nullable
            @Getter
            JavaType.Variable variableType;

            public JavaType getType() {
                return variableType != null ? variableType.getType() : null;
            }

            @SuppressWarnings({"unchecked", "DataFlowIssue"})
            @Override
            public ObjectBindingDeclarations.Binding withType(@Nullable JavaType type) {
                return variableType != null ? withVariableType(variableType.withType(type)) : this;
            }

            public String getSimpleName() {
                return name.getSimpleName();
            }

            @Override
            public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
                return v.visitBinding(this, p);
            }

            public ObjectBindingDeclarations.Binding.Padding getPadding() {
                ObjectBindingDeclarations.Binding.Padding p;
                if (this.padding == null) {
                    p = new ObjectBindingDeclarations.Binding.Padding(this);
                    this.padding = new WeakReference<>(p);
                } else {
                    p = this.padding.get();
                    if (p == null || p.t != this) {
                        p = new ObjectBindingDeclarations.Binding.Padding(this);
                        this.padding = new WeakReference<>(p);
                    }
                }
                return p;
            }

            @RequiredArgsConstructor
            public static class Padding {
                private final ObjectBindingDeclarations.Binding t;

                @Nullable
                public JRightPadded<J.Identifier> getPropertyName() {
                    return t.propertyName;
                }

                public ObjectBindingDeclarations.Binding withPropertyName(@Nullable JRightPadded<J.Identifier> propertyName) {
                    return t.propertyName == propertyName ? t : new ObjectBindingDeclarations.Binding(t.id, t.prefix, t.markers, propertyName, t.name, t.dimensionsAfterName, t.afterVararg, t.initializer, t.variableType);
                }

                @Nullable
                public JLeftPadded<Expression> getInitializer() {
                    return t.initializer;
                }

                public ObjectBindingDeclarations.Binding withInitializer(@Nullable JLeftPadded<Expression> initializer) {
                    return t.initializer == initializer ? t : new ObjectBindingDeclarations.Binding(t.id, t.prefix, t.markers, t.propertyName, t.name, t.dimensionsAfterName, t.afterVararg, initializer, t.variableType);
                }
            }
        }

        public boolean hasModifier(Modifier.Type modifier) {
            return Modifier.hasModifier(getModifiers(), modifier);
        }

        public ObjectBindingDeclarations.Padding getPadding() {
            ObjectBindingDeclarations.Padding p;
            if (this.padding == null) {
                p = new ObjectBindingDeclarations.Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new ObjectBindingDeclarations.Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final ObjectBindingDeclarations t;

            public JContainer<ObjectBindingDeclarations.Binding> getBindings() {
                return t.bindings;
            }

            public ObjectBindingDeclarations withBindings(JContainer<ObjectBindingDeclarations.Binding> bindings) {
                return t.bindings == bindings ? t : new ObjectBindingDeclarations(t.id, t.prefix, t.markers, t.leadingAnnotations, t.modifiers, t.typeExpression, bindings, t.initializer);
            }

            @Nullable
            public JLeftPadded<Expression> getInitializer() {
                return t.initializer;
            }

            public ObjectBindingDeclarations withInitializer(@Nullable JLeftPadded<Expression> initializer) {
                return t.initializer == initializer ? t : new ObjectBindingDeclarations(t.id, t.prefix, t.markers, t.leadingAnnotations, t.modifiers, t.typeExpression, t.bindings, initializer);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @AllArgsConstructor
    final class StatementExpression implements JS, Expression, Statement {

        @With
        @Getter
        UUID id;

        @With
        @Getter
        Statement statement;

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            J j = v.visit(getStatement(), p);
            if (j instanceof StatementExpression) {
                return j;
            } else if (j instanceof Statement) {
                return withStatement((Statement) j);
            }
            return j;
        }

        @Override
        public <J2 extends J> J2 withPrefix(Space space) {
            return (J2) withStatement(statement.withPrefix(space));
        }

        @Override
        public Space getPrefix() {
            return statement.getPrefix();
        }

        @Override
        public <J2 extends Tree> J2 withMarkers(Markers markers) {
            return (J2) withStatement(statement.withMarkers(markers));
        }

        @Override
        public Markers getMarkers() {
            return statement.getMarkers();
        }

        @Nullable
        @Override
        public JavaType getType() {
            return null;
        }

        @Override
        public StatementExpression withType(@Nullable JavaType type) {
            return this;
        }

        @Transient
        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Data
    final class TemplateExpression implements JS, Statement, Expression {

        @Nullable
        @NonFinal
        transient WeakReference<TemplateExpression.Padding> padding;

        @EqualsAndHashCode.Include
        @With
        UUID id;

        @With
        Space prefix;

        @With
        Markers markers;

        @With
        String delimiter;

        @Nullable
        JRightPadded<Expression> tag;

        @Nullable
        public Expression getTag() {
            return tag == null ? null : tag.getElement();
        }

        public TemplateExpression withTag(@Nullable Expression tag) {
            return getPadding().withTag(JRightPadded.withElement(this.tag, tag));
        }

        @With
        List<J> strings;

        @With
        @Nullable
        JavaType type;

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitTemplateExpression(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
        @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
        @Data
        @With
        public static final class Value implements JS {
            UUID id;
            Space prefix;
            Markers markers;
            J tree;
            Space after;
            boolean enclosedInBraces;

            @Override
            public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
                return v.visitTemplateExpressionValue(this, p);
            }
        }

        public TemplateExpression.Padding getPadding() {
            TemplateExpression.Padding p;
            if (this.padding == null) {
                p = new TemplateExpression.Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new TemplateExpression.Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final TemplateExpression t;

            @Nullable
            public JRightPadded<Expression> getTag() {
                return t.tag;
            }

            public TemplateExpression withTag(@Nullable JRightPadded<Expression> tag) {
                return t.tag == tag ? t : new TemplateExpression(t.id, t.prefix, t.markers, t.delimiter, tag, t.strings, t.type);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Data
    final class TypeDeclaration implements JS, Statement, TypedTree {

        @Nullable
        @NonFinal
        transient WeakReference<TypeDeclaration.Padding> padding;

        @EqualsAndHashCode.Include
        @With
        UUID id;

        @With
        Space prefix;

        @With
        Markers markers;

        @With
        List<Annotation> leadingAnnotations;

        @With
        List<Modifier> modifiers;

        @With
        J.Identifier name;

        @Nullable
        @With
        J.TypeParameters typeParameters;

        JLeftPadded<Expression> initializer;

        public Expression getInitializer() {
            return initializer.getElement();
        }

        public TypeDeclaration withInitializer(Expression initializer) {
            return getPadding().withInitializer(JLeftPadded.withElement(this.initializer, initializer));
        }

        @Nullable
        JavaType javaType;

        @Nullable
        @Override
        public JavaType getType() {
            return javaType;
        }

        @SuppressWarnings("unchecked")
        @Override
        public TypeDeclaration withType(@Nullable JavaType javaType) {
            return this.javaType == javaType ? this : new TypeDeclaration(id, prefix, markers, leadingAnnotations, modifiers, name, typeParameters, initializer, javaType);
        }

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitTypeDeclaration(this, p);
        }

        @Override
        @Transient
        public CoordinateBuilder.Statement getCoordinates() {
            return new CoordinateBuilder.Statement(this);
        }

        public TypeDeclaration.Padding getPadding() {
            TypeDeclaration.Padding p;
            if (this.padding == null) {
                p = new TypeDeclaration.Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new TypeDeclaration.Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final TypeDeclaration t;

            public JLeftPadded<Expression> getInitializer() {
                return t.initializer;
            }

            public TypeDeclaration withInitializer(JLeftPadded<Expression> initializer) {
                return t.initializer == initializer ? t : new TypeDeclaration(t.id, t.prefix, t.markers, t.leadingAnnotations, t.modifiers, t.name, t.typeParameters, initializer, t.javaType);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Data
    final class TypeOperator implements JS, Expression, TypedTree, NameTree {

        @Nullable
        @NonFinal
        transient WeakReference<JS.TypeOperator.Padding> padding;

        @With
        @EqualsAndHashCode.Include
        UUID id;

        @With
        Space prefix;

        @With
        Markers markers;

        @With
        JS.TypeOperator.Type operator;

        JLeftPadded<Expression> expression;

        public Expression getExpression() {
            return expression.getElement();
        }

        public JS.TypeOperator withExpression(Expression expression) {
            return getPadding().withExpression(this.expression.withElement(expression));
        }

        @Nullable
        @Override
        public JavaType getType() {
            return expression.getElement().getType();
        }

        @SuppressWarnings("unchecked")
        @Override
        public TypeOperator withType(@Nullable JavaType type) {
            return type == getType() ? this : getPadding().withExpression(this.expression.withElement(this.expression.getElement().withType(type)));
        }

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitTypeOperator(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        public enum Type {
            ReadOnly
        }

        public JS.TypeOperator.Padding getPadding() {
            JS.TypeOperator.Padding p;
            if (this.padding == null) {
                p = new JS.TypeOperator.Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new JS.TypeOperator.Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final JS.TypeOperator t;

            public JLeftPadded<Expression> getExpression() {
                return t.expression;
            }

            public JS.TypeOperator withExpression(JLeftPadded<Expression> expression) {
                return t.expression == expression ? t : new JS.TypeOperator(t.id, t.prefix, t.markers, t.operator, expression);
            }
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    @RequiredArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Data
    final class Union implements JS, Expression, TypeTree {

        @Nullable
        @NonFinal
        transient WeakReference<JS.Union.Padding> padding;

        @With
        @EqualsAndHashCode.Include
        UUID id;

        @With
        Space prefix;

        @With
        Markers markers;

        List<JRightPadded<Expression>> types;

        public List<Expression> getTypes() {
            return JRightPadded.getElements(types);
        }

        public JS.Union withTypes(List<Expression> types) {
            return getPadding().withTypes(JRightPadded.withElements(this.types, types));
        }

        @With
        @Nullable
        JavaType type;

        @Override
        public <P> J acceptJavaScript(JavaScriptVisitor<P> v, P p) {
            return v.visitUnion(this, p);
        }

        @Transient
        @Override
        public CoordinateBuilder.Expression getCoordinates() {
            return new CoordinateBuilder.Expression(this);
        }

        public JS.Union.Padding getPadding() {
            JS.Union.Padding p;
            if (this.padding == null) {
                p = new JS.Union.Padding(this);
                this.padding = new WeakReference<>(p);
            } else {
                p = this.padding.get();
                if (p == null || p.t != this) {
                    p = new JS.Union.Padding(this);
                    this.padding = new WeakReference<>(p);
                }
            }
            return p;
        }

        @RequiredArgsConstructor
        public static class Padding {
            private final JS.Union t;

            public List<JRightPadded<Expression>> getTypes() {
                return t.types;
            }

            public JS.Union withTypes(List<JRightPadded<Expression>> types) {
                return t.types == types ? t : new JS.Union(t.id, t.prefix, t.markers, types, t.type);
            }
        }
    }
}
