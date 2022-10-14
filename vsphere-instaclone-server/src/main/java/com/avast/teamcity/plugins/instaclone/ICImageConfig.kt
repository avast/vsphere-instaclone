package com.avast.teamcity.plugins.instaclone

import com.fasterxml.jackson.annotation.JsonFormat

/**
 *
 * @author Vitasek L.
 */
class ICImageConfig(
    val template: String?,
    val instanceFolder: String?,
    val resourcePool: String?,
    val datastore: String?,
    val maxInstances: Int = Integer.MAX_VALUE,
    val agentPool: Any?,
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val network: List<String> = emptyList(),
    val shutdownTimeout: Long = 30
)