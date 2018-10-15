/*
 * Copyright (c) 2001-2017, Zoltan Farkas All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * Additionally licensed with:
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spf4j.zel;


import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.mvel2.MVEL;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.spf4j.concurrent.DefaultExecutor;
import org.spf4j.zel.vm.CompileException;
import org.spf4j.zel.vm.Program;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 *
 * @author zoly
 */
@State(Scope.Benchmark)
@Fork(2)
@Threads(value = 8)
public class ZelBenchmark {

    private static final Program ZEL_PROG;
    private static final ThreadLocal<Script> GROOVY_PROG;
    private static final Class GROOVY_PROG_CLASZ;
    private static final Serializable MVEL_PROG;
    private static final Expression SPRING_EXP;

    /**
     * A few notes about this benchmark:
     *
     * 1) Zel does overflow check for each math operation and automatically upgrades
     * the data representation, which comes at a overhead.
     * 2) Zel uses reflection to do java method invocations, which is slower than byte code generation
     * that mvel and groovy uses.
     * 3) Spring expression language is slow like hell, and there is no excuse for it :-)
     *
     */

    static {
        final String testScript = "a-b+1+c.length() - d.toString().substring(0, 1).length()";
        try {
            ZEL_PROG = Program.compile(testScript, "a", "b", "c", "d");
        } catch (CompileException ex) {
            throw new RuntimeException(ex);
        }
        final GroovyShell shell = new GroovyShell();
        GROOVY_PROG = new ThreadLocal<Script>() {

            @Override
            protected Script initialValue() {
                return  shell.parse(testScript);
            }

        };
        MVEL_PROG = MVEL.compileExpression(testScript);
        ExpressionParser parser = new SpelExpressionParser();
        SPRING_EXP = parser.parseExpression(
                "['a']-['b']+1+['c'].length() - ['d'].toString().substring(0, 1).length()");

        GroovyClassLoader gcl = new GroovyClassLoader();
        GROOVY_PROG_CLASZ = gcl.parseClass(testScript);
    }

    @TearDown
    public void close() {
        DefaultExecutor.INSTANCE.shutdown();
    }


    @Benchmark
    public Object testZel()
            throws ExecutionException, InterruptedException {
        return ZEL_PROG.execute(3, 2, " ", "bla");
    }

    @Benchmark
    public Object testGroovy() {
        Binding binding = new Binding();
        binding.setVariable("a", 3);
        binding.setVariable("b", 2);
        binding.setVariable("c", " ");
        binding.setVariable("d", "bla");
        Script script = GROOVY_PROG.get();
        script.setBinding(binding);
        return script.run();
    }

    @Benchmark
    public Object testGroovy2() {
        Binding binding = new Binding();
        binding.setVariable("a", 3);
        binding.setVariable("b", 2);
        binding.setVariable("c", " ");
        binding.setVariable("d", "bla");
        return InvokerHelper.createScript(GROOVY_PROG_CLASZ, binding).run();
    }


    @Benchmark
    public Object testMvel() {
        Map vars = new HashMap();
        vars.put("a", 3);
        vars.put("b", 2);
        vars.put("c", " ");
        vars.put("d", "bla");
        return MVEL.executeExpression(MVEL_PROG, vars);

    }

    @Benchmark
    public Object testSpring() {
        Map vars = new HashMap();
        vars.put("a", 3);
        vars.put("b", 2);
        vars.put("c", " ");
        vars.put("d", "bla");
        return SPRING_EXP.getValue(vars, Integer.class);

    }


}
