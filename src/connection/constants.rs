// Copyright (c) 2026 Proton AG
//
// This file is part of ProtonVPN.
//
// ProtonVPN is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// ProtonVPN is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.

use std::time::Duration;

// Maximum delay for exponential backoff used to recover from network down errors. Every platform
// might be comfortable with different delays, depending on:
// - how reliably we know the state of networking in the system
// - how battery-conscious a given platform is
#[cfg(not(feature = "android"))]
pub(crate) const MAX_DELAYED_NETWORK_CHANGE_DURATION : Duration = Duration::from_secs(10);

// Android have a relatively reliable way of detecting network state changes, but it's also
// battery-conscious, so we'll use a longer max delay to avoid excessive battery drain.
#[cfg(feature = "android")]
pub(crate) const MAX_DELAYED_NETWORK_CHANGE_DURATION : Duration = Duration::from_secs(30);

pub(crate) const TOP_SNI_STRATEGY_URLS : &[&str] = &[
    "accounts.google.com",
    "activity.windows.com",
    "analytics.apis.mcafee.com",
    "android.apis.google.com",
    "android.googleapis.com",
    "api.account.samsung.com",
    "api.accounts.firefox.com",
    "api.accuweather.com",
    "api.amazon.com",
    "api.browser.yandex.net",
    "api.ipify.org",
    "api.onedrive.com",
    "api.reasonsecurity.com",
    "api.samsungcloud.com",
    "api.sec.intl.miui.com",
    "api.vk.com",
    "api.weather.com",
    "app-site-association.cdn-apple.com",
    "apps.mzstatic.com",
    "assets.msn.com",
    "backup.googleapis.com",
    "brave-core-ext.s3.brave.com",
    "caldav.calendar.yahoo.com",
    "cc-api-data.adobe.io",
    "cdn.ampproject.org",
    "cdn.cookielaw.org",
    "client.wns.windows.com",
    "cloudflare.com",
    "cloudflare-dns.com",
    "cloudflare-ech.com",
    "config.extension.grammarly.com",
    "connectivitycheck.android.com",
    "connectivitycheck.gstatic.com",
    "courier.push.apple.com",
    "crl.globalsign.com",
    "dc1-file.ksn.kaspersky-labs.com",
    "dl.google.com",
    "dns.google",
    "dns.quad9.net",
    "doh.cleanbrowsing.org",
    "doh.dns.apple.com",
    "doh.opendns.com",
    "doh.pub",
    "ds.kaspersky.com",
    "ecs.office.com",
    "edge.microsoft.com",
    "events.gfe.nvidia.com",
    "excess.duolingo.com",
    "firefox.settings.services.mozilla.com",
    "fonts.googleapis.com",
    "fonts.gstatic.com",
    "gateway-asset.icloud-content.com",
    "gateway.icloud.com",
    "gdmf.apple.com",
    "github.com",
    "go.microsoft.com",
    "go-updater.brave.com",
    "graph.microsoft.com",
    "gs-loc.apple.com",
    "gtglobal.intl.miui.com",
    "hcaptcha.com",
    "imap.gmail.com",
    "imap-mail.outlook.com",
    "imap.mail.yahoo.com",
    "in.appcenter.ms",
    "ipmcdn.avast.com",
    "itunes.apple.com",
    "loc.map.baidu.com",
    "login.live.com",
    "login.microsoftonline.com",
    "m.media-amazon.com",
    "mobile.events.data.microsoft.com",
    "mozilla.cloudflare-dns.com",
    "mtalk.google.com",
    "nimbus.bitdefender.net",
    "ocsp2.apple.com",
    "outlook.office365.com",
    "play-fe.googleapis.com",
    "play.googleapis.com",
    "play.samsungcloud.com",
    "raw.githubusercontent.com",
    "s3.amazonaws.com",
    "safebrowsing.googleapis.com",
    "s.alicdn.com",
    "self.events.data.microsoft.com",
    "settings-win.data.microsoft.com",
    "setup.icloud.com",
    "sirius.mwbsys.com",
    "spoc.norton.com",
    "ssl.gstatic.com",
    "translate.goo",
    "unpkg.com",
    "update.googleapis.com",
    "weatherapi.intl.xiaomi.com",
    "weatherkit.apple.com",
    "westus-0.in.applicationinsights.azure.com",
    "www.googleapis.com",
    "www.gstatic.com",
    "www.msftconnecttest.com",
    "www.msftncsi.com",
    "www.ntppool.org",
    "www.pool.ntp.org",
    "www.recaptcha.net",
];
