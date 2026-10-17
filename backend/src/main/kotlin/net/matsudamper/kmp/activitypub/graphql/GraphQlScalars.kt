package net.matsudamper.kmp.activitypub.graphql

import java.util.Locale
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId

object GraphQlScalars {
    /**
     * JavaScript の数値に収まらない桁でも崩れないよう、文字列でやり取りする
     */
    val userId: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("UserId")
            .coercing(UserIdCoercing)
            .build()

    val passkeyCredentialId: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("PasskeyCredentialId")
            .coercing(Base64UrlCoercing(name = "PasskeyCredentialId", type = PasskeyCredentialId::class.java, wrap = ::PasskeyCredentialId, unwrap = PasskeyCredentialId::value))
            .build()

    private object UserIdCoercing : Coercing<AccountId, String> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): String {
            val accountId = dataFetcherResult as? AccountId ?: throw CoercingSerializeException("AccountId ではない: $dataFetcherResult")
            return accountId.value.toString()
        }

        override fun parseValue(
            input: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): AccountId {
            val value = (input as? String)?.toLongOrNull() ?: throw CoercingParseValueException("UserId ではない: $input")
            return AccountId(value)
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): AccountId {
            val value = (input as? StringValue)?.value?.toLongOrNull() ?: throw CoercingParseLiteralException("UserId ではない: $input")
            return AccountId(value)
        }
    }

    /**
     * 値は base64url（パディング無し）の文字列で受け渡す
     */
    private class Base64UrlCoercing<T : Any>(
        private val name: String,
        private val type: Class<T>,
        private val wrap: (String) -> T,
        private val unwrap: (T) -> String,
    ) : Coercing<T, String> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): String {
            if (!type.isInstance(dataFetcherResult)) throw CoercingSerializeException("$name ではない: $dataFetcherResult")
            return unwrap(type.cast(dataFetcherResult))
        }

        override fun parseValue(
            input: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): T {
            val value = (input as? String)?.takeIf { BASE64URL.matches(it) } ?: throw CoercingParseValueException("$name ではない")
            return wrap(value)
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): T {
            val value = (input as? StringValue)?.value?.takeIf { BASE64URL.matches(it) } ?: throw CoercingParseLiteralException("$name ではない")
            return wrap(value)
        }
    }

    private val BASE64URL = Regex("^[A-Za-z0-9_-]+$")
}
