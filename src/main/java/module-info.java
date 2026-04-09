module com.identitycrisis {
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.media;

    exports com.identitycrisis.client;
    exports com.identitycrisis.server;
    exports com.identitycrisis.shared.model;
    exports com.identitycrisis.shared.net;
    exports com.identitycrisis.shared.util;
}
