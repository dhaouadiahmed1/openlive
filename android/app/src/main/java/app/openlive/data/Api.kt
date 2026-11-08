package app.openlive.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** Default base URL: Android emulator talking to a backend on the host PC.
 *  On a real phone, replace with your server, e.g. https://openlive.example.com/
 *  (set it in the app's Settings screen — no URL is hardcoded anywhere else). */
const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

interface OpenLiveApi {
    @GET("api/health")
    suspend fun health(): Map<String, Any>

    @GET("api/settings")
    suspend fun settings(): SettingsResponse

    @GET("api/topics")
    suspend fun topics(): TopicsResponse

    @GET("api/live")
    suspend fun live(
        @Query("topic") topic: String? = null,
        @Query("limit") limit: Int = 200
    ): LiveResponse

    @GET("api/live/{id}")
    suspend fun liveOne(@Path("id") id: String): Channel

    @GET("api/search")
    suspend fun search(@Query("q") q: String): SearchResponse

    @POST("api/activate")
    suspend fun activate(@Body body: ActivateRequest): ActivateResponse
}

fun createApi(baseUrl: String): OpenLiveApi {
    val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
    val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(url)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenLiveApi::class.java)
}
