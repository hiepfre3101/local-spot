package com.localspot.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IpPrefixesTest {

    @Test
    void ipv4UsesSlash24() {
        assertThat(IpPrefixes.of("203.0.113.7")).isEqualTo("203.0.113.0/24");
        assertThat(IpPrefixes.of("203.0.113.250")).isEqualTo("203.0.113.0/24");
        assertThat(IpPrefixes.of("203.0.114.7")).isEqualTo("203.0.114.0/24");
        // IPv4 dạng IPv6 được coi là IPv4
        assertThat(IpPrefixes.of("::ffff:203.0.113.9")).isEqualTo("203.0.113.0/24");
    }

    @Test
    void ipv6UsesSlash64() {
        assertThat(IpPrefixes.of("2001:db8:aaaa:bbbb:1:2:3:4")).isEqualTo("2001:db8:aaaa:bbbb::/64");
        assertThat(IpPrefixes.of("2001:0db8:aaaa:bbbb::99")).isEqualTo("2001:db8:aaaa:bbbb::/64");
        assertThat(IpPrefixes.of("::1")).isEqualTo("0:0:0:0::/64");
    }

    @Test
    void neverResolvesHostNames() {
        assertThat(IpPrefixes.of("example.com")).isEqualTo("unknown");
        assertThat(IpPrefixes.of(null)).isEqualTo("unknown");
        assertThat(IpPrefixes.of("")).isEqualTo("unknown");
    }
}
