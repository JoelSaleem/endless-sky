// Minimal libuuid-compatible shim for Android (the NDK has no <uuid/uuid.h>).
// Implements only the subset Endless Sky's EsUuid uses. v4 UUIDs are generated
// from arc4random_buf (always available on bionic). Header-only; nothing to link.
#pragma once

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>   // arc4random_buf
#include <string.h>

typedef unsigned char uuid_t[16];

static inline void uuid_generate_random(uuid_t out)
{
	arc4random_buf(out, 16);
	out[6] = (unsigned char)((out[6] & 0x0F) | 0x40); // version 4
	out[8] = (unsigned char)((out[8] & 0x3F) | 0x80); // variant 1 (RFC 4122)
}

static inline void uuid_copy(uuid_t dst, const uuid_t src)
{
	memcpy(dst, src, 16);
}

static inline int uuid_is_null(const uuid_t uu)
{
	for(int i = 0; i < 16; ++i)
		if(uu[i])
			return 0;
	return 1;
}

static inline int uuid_compare(const uuid_t a, const uuid_t b)
{
	return memcmp(a, b, 16);
}

static inline void uuid_unparse_lower(const uuid_t uu, char *out)
{
	snprintf(out, 37,
		"%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
		uu[0], uu[1], uu[2], uu[3], uu[4], uu[5], uu[6], uu[7],
		uu[8], uu[9], uu[10], uu[11], uu[12], uu[13], uu[14], uu[15]);
}

// Returns 0 on success, -1 on malformed input (matches libuuid).
static inline int uuid_parse(const char *in, uuid_t uu)
{
	int i = 0, b = 0;
	while(in[i] && b < 16)
	{
		if(in[i] == '-')
		{
			++i;
			continue;
		}
		unsigned int byte;
		if(sscanf(in + i, "%2x", &byte) != 1)
			return -1;
		uu[b++] = (unsigned char)byte;
		i += 2;
	}
	return (b == 16) ? 0 : -1;
}
