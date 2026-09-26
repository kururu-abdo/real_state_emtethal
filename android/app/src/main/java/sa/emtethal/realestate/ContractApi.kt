package sa.emtethal.realestate

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.*

interface ContractApi {
    @GET("api/contracts") suspend fun contracts(): List<Contract>
    @GET("api/contracts/{id}") suspend fun contract(@Path("id") id: String): Contract
    @Multipart @POST("api/documents") suspend fun upload(@Part file: MultipartBody.Part): UploadedDocument
    @POST("api/contracts") suspend fun create(@Body request: StartRequest): Contract
    @POST("api/contracts/{id}/review") suspend fun review(@Path("id") id: String, @Body request: Approval): Contract
    @Streaming @GET("api/contracts/{id}/pdf") suspend fun pdf(@Path("id") id: String): ResponseBody
}
object ApiFactory {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun normalizeUrl(value: String, allowHttp: Boolean): String {
        val url = value.trim().toHttpUrlOrNull() ?: error("عنوان السيرفر غير صحيح.")
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
            "استخدم عنوان السيرفر بدون كلمات مرور أو query أو fragment."
        }
        require(url.isHttps || allowHttp) { "نسخة الإصدار تتطلب HTTPS." }
        return url.toString().trimEnd('/') + "/"
    }
    fun create(baseUrl: String, token: String, allowHttp: Boolean = BuildConfig.DEBUG): ContractApi {
        require(token.isNotBlank()) { "أدخل مفتاح الدخول." }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES).writeTimeout(2, TimeUnit.MINUTES)
            .callTimeout(6, TimeUnit.MINUTES)
            // Mutations are not automatically replayed if their response is lost.
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer ${token.trim()}").build())
            }.build()
        return Retrofit.Builder().baseUrl(normalizeUrl(baseUrl, allowHttp)).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(ContractApi::class.java)
    }
}
