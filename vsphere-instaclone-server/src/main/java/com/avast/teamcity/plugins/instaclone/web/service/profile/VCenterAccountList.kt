package com.avast.teamcity.plugins.instaclone.web.service.profile

/**
 *
 * @author Vitasek L.
 */
data class VCenterAccountList(val accounts: List<VCenterAccount> = emptyList()) {
}

data class VCenterAccountInfo(
    val id: String,
    val url: String
)

data class VCenterAccountListInfo(val accounts: List<VCenterAccountInfo> = emptyList()) {
}
