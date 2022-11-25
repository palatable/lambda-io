package research.lambda.effect.io.fiber;

final class ExceptionOutsideOfFiber extends FatalFiberError {
    ExceptionOutsideOfFiber(Throwable cause) {
        super("An exception was thrown from outside of a `Fiber` lexical closure. This is likely due"
                      + " to a partial function used in an `fmap`, `flatMap`, or other type of"
                      + " compositional function. These functions must *never* throw, as they are essential"
                      + " to the assembly of the fiber spine. Instead, the partial function should be"
                      + " embedded inside the resulting fiber.", cause);
    }
}
