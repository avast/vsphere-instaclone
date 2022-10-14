package com.avast.teamcity.plugins.instaclone.web.service.profile

import org.apache.commons.codec.digest.DigestUtils
import java.util.*

class VCenterAccount(
    val id: String,
    val url: String,
    val username: String?,
    val password: String?
) {

    override fun toString(): String {
        return StringJoiner(", ", VCenterAccount::class.java.simpleName + "[", "]")
            .add("id='$id'")
            .add("url='$url'")
            .add("username='***HIDDEN***'")
            .add("password='***HIDDEN***'")
            .toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VCenterAccount) return false

        if (id != other.id) return false
        if (url != other.url) return false
        if (username != other.username) return false
        if (password != other.password) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + (username?.hashCode() ?: 0)
        result = 31 * result + (password?.hashCode() ?: 0)
        return result
    }

    fun hash(): String {
        return DigestUtils.sha256Hex(id + url + (username ?: "") + (password ?: ""))
    }
}