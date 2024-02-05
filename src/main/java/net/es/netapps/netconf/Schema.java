package net.es.netapps.netconf;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Schema {
    private String element;
    private String namespace;
}