package dev.kanakku.identity.service;

import java.util.function.Supplier;

/**
 * Work split around a slow call. {@code provisional} is the answer decided and stored inside the
 * transaction; {@code finish} then runs with no transaction open (it makes the SMS provider call)
 * and returns the answer to send, which may differ from the provisional one when the call failed.
 */
public record Deferred<T>(T provisional, Supplier<T> finish) {}
