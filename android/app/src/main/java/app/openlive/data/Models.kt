package app.openlive.data

import com.google.gson.annotations.SerializedName

data class Channel(
    val id: String,
    val name: String,
    val topic: String,
    val country: String?,
    val language: String?,
    val logo: String?,
    @SerializedName("stream_url") val streamUrl: String,
    val quality: String?,
    val source: String?,
    @SerializedName("iptv_org_id") val iptvOrgId: String?
)

data class Topic(val name: String, val count: Int)

data class TopicsResponse(val topics: List<Topic> = emptyList())

data class LiveResponse(
    val total: Int = 0,
    val returned: Int = 0,
    val channels: List<Channel> = emptyList()
)

data class SearchResponse(
    val q: String = "",
    val returned: Int = 0,
    val channels: List<Channel> = emptyList()
)

data class ActivateRequest(val code: String)

data class ActivateResponse(val ok: Boolean, val message: String)

data class SettingsResponse(
    @SerializedName("app_name") val appName: String = "OpenLive",
    val version: String = "",
    @SerializedName("activation_required") val activationRequired: Boolean = true
)
