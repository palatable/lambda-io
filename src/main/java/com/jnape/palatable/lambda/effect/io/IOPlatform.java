package com.jnape.palatable.lambda.effect.io;

import com.jnape.palatable.lambda.effect.io.interpreter.TailExpr;
import com.jnape.palatable.lambda.effect.io.interpreter.TailExpr.Recur;
import com.jnape.palatable.lambda.effect.io.interpreter.TailExpr.Return;
import com.jnape.palatable.lambda.effect.io.interpreter.RunSync;

import static com.jnape.palatable.lambda.effect.io.interpreter.RunSync.runSync;

public interface IOPlatform {

    <A> A unsafeInterpretFully(IO<A> io);

    static IOPlatform system() {
        return Constants.SYSTEM;
    }
}

interface Constants {
    IOPlatform SYSTEM = new IOPlatform() {
        @Override
        public <A> A unsafeInterpretFully(IO<A> io) {
            RunSync<A>         instance = runSync();
            TailExpr<IO<A>, A> next     = io.interpret(instance);
            while (next instanceof Recur<IO<A>, A> i) {
                next = i.a().interpret(instance);
            }
            return ((Return<IO<A>, A>) next).b();
        }
    };
}