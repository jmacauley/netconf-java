package net.es.netapps.netconf;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Document {
    private String element;
    private String namespace;
    private String document;
}