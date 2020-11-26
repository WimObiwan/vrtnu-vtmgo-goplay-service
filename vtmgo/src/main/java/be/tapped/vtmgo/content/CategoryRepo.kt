package be.tapped.vtmgo.content

import arrow.core.Either
import arrow.core.computations.either
import be.tapped.common.executeAsync
import be.tapped.common.validateResponse
import be.tapped.vtmgo.common.HeaderBuilder
import be.tapped.vtmgo.content.ApiResponse.Failure.JsonParsingException
import be.tapped.vtmgo.profile.JWT
import be.tapped.vtmgo.profile.Profile
import be.tapped.vtmgo.profile.VTMGOProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal class JsonCategoryParser {
    suspend fun parse(json: String): Either<ApiResponse.Failure, CategoryResponse> =
        Either.catch<CategoryResponse> { Json.decodeFromString(json) }.mapLeft(::JsonParsingException)
}

interface CategoryRepo {
    suspend fun fetchCategories(jwt: JWT, profile: Profile): Either<ApiResponse.Failure, ApiResponse.Success.Categories>
}

internal class HttpCategoryRepo(
    private val client: OkHttpClient,
    private val baseContentHttpUrlBuilder: BaseContentHttpUrlBuilder,
    private val headerBuilder: HeaderBuilder,
    private val jsonCategoryParser: JsonCategoryParser,
) : CategoryRepo {

    override suspend fun fetchCategories(jwt: JWT, profile: Profile): Either<ApiResponse.Failure, ApiResponse.Success.Categories> =
        withContext(Dispatchers.IO) {
            val response = client.executeAsync(
                Request.Builder()
                    .headers(headerBuilder.authenticationHeaders(jwt, profile))
                    .get()
                    .url(constructUrl(profile.product))
                    .build()
            )

            either {
                !response.validateResponse { ApiResponse.Failure.NetworkFailure(response.code, response.request) }
                val responseBody = !Either.fromNullable(response.body).mapLeft { ApiResponse.Failure.EmptyJson }
                val categoryResponse = !jsonCategoryParser.parse(responseBody.string())
                ApiResponse.Success.Categories(categoryResponse.categories)
            }
        }

    private fun constructUrl(vtmGoProduct: VTMGOProduct): HttpUrl =
        baseContentHttpUrlBuilder.constructBaseContentUrl(vtmGoProduct)
            .addPathSegments("catalog/filters")
            .addQueryParameter("pageSize", "2000")
            .build()
}
