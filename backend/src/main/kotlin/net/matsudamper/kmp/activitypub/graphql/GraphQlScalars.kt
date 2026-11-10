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
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId
import net.matsudamper.kmp.activitypub.shared.TimelinePostId

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
            .coercing(StringIdCoercing(name = "PasskeyCredentialId", type = PasskeyCredentialId::class.java, wrap = ::PasskeyCredentialId, unwrap = PasskeyCredentialId::value))
            .build()

    val noteImageId: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("NoteImageId")
            .coercing(StringIdCoercing(name = "NoteImageId", type = NoteImagePublicId::class.java, wrap = ::NoteImagePublicId, unwrap = NoteImagePublicId::value))
            .build()

    /**
     * JavaScript の数値に収まらない桁でも崩れないよう、文字列でやり取りする
     */
    val followingGroupId: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("FollowingGroupId")
            .coercing(LongIdCoercing(name = "FollowingGroupId", type = FollowingGroupId::class.java, wrap = ::FollowingGroupId, unwrap = FollowingGroupId::value))
            .build()

    val timelinePostId: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("TimelinePostId")
            .coercing(StringIdCoercing(name = "TimelinePostId", type = TimelinePostId::class.java, wrap = ::TimelinePostId, unwrap = TimelinePostId::value))
            .build()

    /**
     * 中身は multipart の部分から [GraphQlRoutes] が変数に差し込む。文字列やリテラルでは受け取らない
     */
    val upload: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("Upload")
            .coercing(UploadCoercing)
            .build()

    private object UploadCoercing : Coercing<GraphQlUpload, Nothing> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): Nothing = throw CoercingSerializeException("Upload は返さない")

        override fun parseValue(
            input: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): GraphQlUpload = input as? GraphQlUpload ?: throw CoercingParseValueException("Upload は multipart のファイルで送ること")

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): GraphQlUpload = throw CoercingParseLiteralException("Upload は multipart のファイルで送ること")
    }

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

    private class LongIdCoercing<T : Any>(
        private val name: String,
        private val type: Class<T>,
        private val wrap: (Long) -> T,
        private val unwrap: (T) -> Long,
    ) : Coercing<T, String> {
        override fun serialize(
            dataFetcherResult: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): String {
            if (!type.isInstance(dataFetcherResult)) throw CoercingSerializeException("$name ではない: $dataFetcherResult")
            return unwrap(type.cast(dataFetcherResult)).toString()
        }

        override fun parseValue(
            input: Any,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): T {
            val value = (input as? String)?.toLongOrNull() ?: throw CoercingParseValueException("$name ではない: $input")
            return wrap(value)
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): T {
            val value = (input as? StringValue)?.value?.toLongOrNull() ?: throw CoercingParseLiteralException("$name ではない: $input")
            return wrap(value)
        }
    }

    private class StringIdCoercing<T : Any>(
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
            val value = input as? String ?: throw CoercingParseValueException("$name ではない")
            return wrap(value)
        }

        override fun parseLiteral(
            input: Value<*>,
            variables: CoercedVariables,
            graphQLContext: GraphQLContext,
            locale: Locale,
        ): T {
            val value = (input as? StringValue)?.value ?: throw CoercingParseLiteralException("$name ではない")
            return wrap(value)
        }
    }
}
