/*
 * Copyright (c) 2019, Fraunhofer AISEC. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *                    $$$$$$\  $$$$$$$\   $$$$$$\
 *                   $$  __$$\ $$  __$$\ $$  __$$\
 *                   $$ /  \__|$$ |  $$ |$$ /  \__|
 *                   $$ |      $$$$$$$  |$$ |$$$$\
 *                   $$ |      $$  ____/ $$ |\_$$ |
 *                   $$ |  $$\ $$ |      $$ |  $$ |
 *                   \$$$$$   |$$ |      \$$$$$   |
 *                    \______/ \__|       \______/
 *
 */

package de.fraunhofer.aisec.cpg.frontends.cpp;

import de.fraunhofer.aisec.cpg.frontends.Handler;
import de.fraunhofer.aisec.cpg.graph.ConstructExpression;
import de.fraunhofer.aisec.cpg.graph.Expression;
import de.fraunhofer.aisec.cpg.graph.NodeBuilder;
import org.eclipse.cdt.core.dom.ast.IASTInitializer;
import org.eclipse.cdt.core.dom.ast.IASTInitializerClause;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTConstructorInitializer;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTEqualsInitializer;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTInitializerList;

public class InitializerHandler extends Handler<Expression, IASTInitializer, CXXLanguageFrontend> {

  public InitializerHandler(CXXLanguageFrontend lang) {
    super(Expression::new, lang);

    map.put(
        CPPASTConstructorInitializer.class,
        ctx -> handleConstructorInitializer((CPPASTConstructorInitializer) ctx));
    map.put(
        CPPASTEqualsInitializer.class,
        ctx -> handleEqualsInitializer((CPPASTEqualsInitializer) ctx));

    /* Todo Initializer List is handled in ExpressionsHandler that actually handles InitializerClauses often used where
        one expects an expression.
    */
    map.put(
        CPPASTInitializerList.class,
        ctx -> lang.getExpressionHandler().handle((CPPASTInitializerList) ctx));
  }

  private Expression handleConstructorInitializer(CPPASTConstructorInitializer ctx) {
    // ctx.getRawSignature(); only returns "(1)" for "new Botan(1)". no way to get Botan(1) except:
    String code = ctx.getRawSignature();
    if (ctx.getParent() != null && ctx.getParent().getRawSignature() != null) {
      if (ctx.getParent().getRawSignature().startsWith("new ")) {
        code = ctx.getParent().getRawSignature().substring(4);
      } else {
        code = ctx.getParent().getRawSignature();
      }
    }
    ConstructExpression constructExpression = NodeBuilder.newConstructExpression(code);
    // TODO: parse constructor

    int i = 0;
    for (IASTInitializerClause argument : ctx.getArguments()) {
      Expression arg = lang.getExpressionHandler().handle(argument);

      arg.setArgumentIndex(i);

      constructExpression.getArguments().add(arg);
      arg.addNextDFG(constructExpression);

      i++;
    }

    return constructExpression;
  }

  private Expression handleEqualsInitializer(CPPASTEqualsInitializer ctx) {
    return lang.getExpressionHandler().handle(ctx.getInitializerClause());
  }
}
