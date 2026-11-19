package com.novelex.dramas;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import okhttp3.dnsoverhttps.DnsOverHttps;

final class PlaybackNetwork {
    private static final OkHttpClient CLIENT = create();
    static OkHttpClient client() { return CLIENT; }

    private static OkHttpClient create() {
        OkHttpClient bootstrap = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS).build();
        try {
            Dns cloudflare = new DnsOverHttps.Builder().client(bootstrap)
                .url(HttpUrl.get("https://cloudflare-dns.com/dns-query"))
                .bootstrapDnsHosts(InetAddress.getByAddress(new byte[]{1,1,1,1}),
                    InetAddress.getByAddress(new byte[]{1,0,0,1})).includeIPv6(false).build();
            Dns google = new DnsOverHttps.Builder().client(bootstrap)
                .url(HttpUrl.get("https://dns.google/dns-query"))
                .bootstrapDnsHosts(InetAddress.getByAddress(new byte[]{8,8,8,8}),
                    InetAddress.getByAddress(new byte[]{8,8,4,4})).includeIPv6(false).build();
            return new OkHttpClient.Builder().dns(host -> {
                try { return cloudflare.lookup(host); }
                catch (UnknownHostException first) {
                    try { return google.lookup(host); }
                    catch (UnknownHostException second) { return Dns.SYSTEM.lookup(host); }
                }
            }).connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build();
        } catch (UnknownHostException impossible) { return new OkHttpClient(); }
    }
}
