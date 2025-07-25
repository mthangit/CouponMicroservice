package org.couponmanagement.grpc.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "app.identity")
@Data
public class GrpcClientMetadataProperties {
    
    private String xServiceId;
    private String xClientKey;
}
