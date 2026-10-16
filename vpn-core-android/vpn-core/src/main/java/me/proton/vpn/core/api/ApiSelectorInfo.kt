/*
 * Copyright (c) 2026. Proton AG
 *
 * This file is part of ProtonVPN.
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or later.
 */

package me.proton.vpn.core.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Session fork selector and associated authentication cookies.
 *  The cookie values must be retained when crossing the Android service boundary.
 */
@Parcelize
data class ApiSelectorInfo(val selector: String, val cookies: List<ApiSelectorCookie>) : Parcelable {
    override fun toString(): String = "ApiSelectorInfo([redacted])"
}

@Parcelize
data class ApiSelectorCookie(val name: String, val value: String) : Parcelable {
    override fun toString(): String = "ApiSelectorCookie([redacted])"
}
