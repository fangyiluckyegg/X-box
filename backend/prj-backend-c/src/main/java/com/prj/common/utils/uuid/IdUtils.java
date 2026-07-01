package com.prj.common.utils.uuid;

public class IdUtils
{

    public static String simpleUUID()
    {
        return UUID.randomUUID().toString(true);
    }

    public static String fastUUID()
    {
        return UUID.fastUUID().toString();
    }
}


