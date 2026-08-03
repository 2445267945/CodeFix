package com.xd.util;

import org.springframework.util.DigestUtils;

public class Md5Utils {
    public static String MD5Digest(String code) {
        return DigestUtils.md5DigestAsHex(code.getBytes());
    }
}
