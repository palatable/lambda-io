package com.jnape.palatable.lambda.effect.io.fiber2;

abstract class FatalFiberError extends Error {
    FatalFiberError(String message, Throwable cause) {
        super(message, cause);
    }
}
