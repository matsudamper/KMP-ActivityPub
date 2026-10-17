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
}
