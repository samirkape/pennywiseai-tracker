package com.spendly.tracker.connector.data.repository

import javax.inject.Qualifier

/** Qualifies the OpenAI protocol adapter. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY
)
annotation class OpenAiClient

/** Qualifies the Anthropic protocol adapter. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY
)
annotation class AnthropicClient
