package research.lambda.effect.io;

import research.lambda.effect.io.fiber2.old.FiberResult;
import com.jnape.palatable.lambda.functions.Fn1;

import java.util.function.Consumer;

public interface Interpreter<A, R> {

    R interpret(A a);

    R interpret(Consumer<? super Consumer<? super FiberResult<A>>> k);

    <Z> R interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF);

    <Z> R interpret(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f);
}
