/*
 * Copyright (C) 2016 Talsma ICT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.talsmasoftware.umldoclet.rendering;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import nl.talsmasoftware.umldoclet.UMLDocletConfig;
import nl.talsmasoftware.umldoclet.rendering.indent.IndentingPrintWriter;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Renderer to produce PlantUML output for a single class.
 *
 * @author <a href="mailto:info@talsma-software.nl">Sjoerd Talsma</a>
 */
public class ClassRenderer extends Renderer {

    protected final ClassDoc classDoc;

    public ClassRenderer(UMLDocletConfig config, UMLDiagram diagram, ClassDoc classDoc) {
        super(config, diagram);
        this.classDoc = requireNonNull(classDoc, "No class documentation provided.");
        for (FieldDoc field : classDoc.fields(false)) {
            children.add(new FieldRenderer(config, diagram, field));
        }
        for (ConstructorDoc constructor : classDoc.constructors(false)) {
            children.add(new MethodRenderer(config, diagram, constructor));
        }
        List<MethodRenderer> abstractMethods = new ArrayList<>();
        for (MethodDoc method : classDoc.methods(false)) {
            if (method.isAbstract()) {
                abstractMethods.add(new MethodRenderer(config, diagram, method));
            } else {
                children.add(new MethodRenderer(config, diagram, method));
            }
        }
        children.addAll(abstractMethods); // abstract methods come last in our UML diagrams.
    }

    protected String umlType() {
        return classDoc.isEnum() ? "enum"
                : classDoc.isInterface() ? "interface"
                : classDoc.isAbstract() ? "abstract class"
                : "class";
    }

    public IndentingPrintWriter writeTo(IndentingPrintWriter out) {
        currentDiagram.encounteredTypes.add(classDoc.qualifiedTypeName());
        // out.println(String.format("' Class \"%s.%s\":", classDoc.containingPackage().name(), classDoc.name()));
        out.append(umlType()).append(' ').append(classDoc.qualifiedTypeName()).append(" {").newline();
        return writeChildrenTo(out).append("}").newline().newline();
    }

}
