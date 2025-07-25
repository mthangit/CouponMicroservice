package org.couponmanagement.grpc.client;

import io.grpc.Context;

import java.util.List;

public class AuthContext {
    public static final Context.Key<String> SERVICE_ID_KEY = Context.key("x-service-id");
    public static final Context.Key<String> CLIENT_KEY_KEY = Context.key("x-client-key");
    public static final Context.Key<List<String>> LIST_PERMISSIONS_KEY = Context.key("list-permissions");

    public static String currentServiceId() {
        return SERVICE_ID_KEY.get();
    }
    public static String currentClientKey() {
        return CLIENT_KEY_KEY.get();
    }
    public static List<String> currentListPermissions() {
        return LIST_PERMISSIONS_KEY.get();
    }
}
