package net.es.netapps.netconf;

import com.google.common.base.Strings;
import net.juniper.netconf.NetconfException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Nokia specific refinement for the Discoverable class.
 */
public class Nokia extends Discoverable {
    private static final Logger logger = LoggerFactory.getLogger(Nokia.class);

    // Count the number of errors we have encountered during discovery.
    private int errorCount = 0;

    /**
     * Initialize all parameters needed to perform discovery on the Nokia devices.
     *
     * @param hostname
     * @param username
     * @param password
     * @param timeout
     */
    public Nokia(String hostname, String username, String password, long timeout) {
        super(hostname, username, password, timeout);
    }

    // We discover based off of known schema versions.  We are not parsing the NETCONF
    // details, but we need to know the root structure and specific behaviours to get
    // the XML documents out of the device.  We can use the NETCONF <hello /> exchange
    // to determine compatibility of our discovery code.

    // Nokia OS version 21.x uses the following lineup.
    private static final String OS_VERSION = "urn:nokia.com:sros:ns:yang:sr:major-release-";
    private static final String OS_VERSION_21 = "urn:nokia.com:sros:ns:yang:sr:major-release-21";
    private static final String OS_VERSION_22 = "urn:nokia.com:sros:ns:yang:sr:major-release-22";
    private static final String CONF_VERSION_2019_12_03 = "urn:nokia.com:sros:ns:yang:sr:conf?module=nokia-conf&revision=2019-12-03";
    private static final String STATE_VERSION_2019_12_03 = "urn:nokia.com:sros:ns:yang:sr:state?module=nokia-state&revision=2019-12-03";

    /**
     * Perform the act of discovery returning the discovered documents.
     */
    public List<Document> discover() {
        List<Document> results = new ArrayList<>();

        try {
            // Connect to the Nokia device.
            this.connect();

            // Discovery capabilities from the device.
            List<String> capabilities = this.getCapabilities();
            //capabilities.forEach(logger::info);

            // Verify the Nokia is running the correct OS version for discovery.
            String version = getOsVersion(capabilities);
            if (Strings.isNullOrEmpty(version)) {
                // We cannot determine the version of Nokia device.
                logger.error("[Nokia] {} unknown OS version", getHostname());
            } else {
                logger.info("[Nokia] os version to {}.", version);

                if (version.contains(OS_VERSION_21) || version.contains(OS_VERSION_22)) {
                    results.addAll(discoverOsVersion2x(capabilities));
                } else {
                    logger.error("[Nokia] {} is running incompatible OS version, expected {}",
                        getHostname(), OS_VERSION_21);
                    errorCount++;

                    // Do best effort to discover this device.
                    results.addAll(discoverOsVersion2x(capabilities));
                }
            }
            this.disconnect();
        } catch (NetconfException ex) {
            logger.error("[Nokia] {} encountered an error", getHostname(), ex);
            errorCount++;
        }

        logger.info("[Discover] {} discovery complete, errorCount = {}.", getHostname(), errorCount);
        return results;
    }

    /**
     * We have seen some minor differences between SROS versions 20, 21, and 22
     * in the structure of the root elements.  We will need to account for these
     * differences as we move forward with different element lists.
     *
     * @param capabilities
     * @return
     */
    private List<Document> discoverOsVersion2x(List<String> capabilities) {
        List<Document> results = new ArrayList<>();

        // Verify the Nokia is running the correct <config /> YANG schema version for discovery.
        if (!capabilities.contains(CONF_VERSION_2019_12_03)) {
            logger.error("[Nokia] {} is running incompatible config schema version, expected {}",
                getHostname(), CONF_VERSION_2019_12_03);
            errorCount++;
        }

        // Verify the Nokia is running the correct <state /> YANG schema version for discovery.
        if (!capabilities.contains(STATE_VERSION_2019_12_03)) {
            logger.error("[Nokia] {} is running incompatible state schema version, expected {}",
                getHostname(), STATE_VERSION_2019_12_03);
            errorCount++;
        }

        // Discover the Nokia <state /> YANG tree.  This is too large for a single <get /> RPC operation,
        // so we iterate over the individual elements.
        for (Schema schema : STATE_SCHEMA_2019_12_03) {
            try {
                results.add(Document.builder()
                    .element(schema.getElement())
                    .namespace(schema.getNamespace())
                    .document(getRunningConfigAndState(schema.getElement(), Nokia.getStateSchemaFilter(schema)))
                    .build());
            } catch (NetconfException ex) {
                logger.error("[Nokia] {} failed to retrieve state schema for {}",
                    getHostname(), schema.getElement(), ex);
                errorCount++;
            }
        }
        return results;
    }

    /**
     * We should be looking into the CPM module's <software-code-version/> attribute for
     * the full versioning information, but we can cheat and get the major version this
     * way.
     *
     * @param capabilities
     * @return
     */
    private String getOsVersion(List<String> capabilities) {
        return capabilities.stream()
            .filter(s -> s.contains(OS_VERSION))
            .findFirst()
            .orElse(null);
    }

    /**
     * This formats the filter needed for a configuration and state <get/> NETCONF
     * operation.
     *
     * @param schema
     * @return
     */
    public static String getStateSchemaFilter(Schema schema) {
        return String.format(FILTER, String.format(STATE, schema.getElement()));
    }

    // NETCONF RPC message template for a filter.
    private static final String FILTER = "<filter type=\"subtree\">%s</filter>";

    // Configuration element for the Nokia configuration retrieval.
    private static final String CONFIGURATION = "<configure xmlns=\"urn:nokia.com:sros:ns:yang:sr:conf\">%s</configure>";

    // State element for the Nokia configuration and state retrieval.
    private static final String STATE = "<state xmlns=\"urn:nokia.com:sros:ns:yang:sr:state\">%s</state>";

    // List of Nokia state schema we need to discover.
    private static final List<Schema> STATE_SCHEMA_2019_12_03 = List.of(
        Schema.builder().element("<aaa />").namespace("urn:nokia.com:sros:ns:yang:sr:state:aaa").build(),
        Schema.builder().element("<application-assurance />").namespace("urn:nokia.com:sros:ns:yang:sr:state:application-assurance").build(),
        Schema.builder().element("<aps />").namespace("urn:nokia.com:sros:ns:yang:sr:state:aps").build(),
        Schema.builder().element("<bfd />").namespace("urn:nokia.com:sros:ns:yang:sr:state:bfd").build(),
        Schema.builder().element("<call-trace />").namespace("urn:nokia.com:sros:ns:yang:sr:state:call-trace").build(),
        Schema.builder().element("<card />").namespace("urn:nokia.com:sros:ns:yang:sr:state:card").build(),
        Schema.builder().element("<cflowd />").namespace("urn:nokia.com:sros:ns:yang:sr:state:cflowd").build(),
        Schema.builder().element("<chassis />").namespace("urn:nokia.com:sros:ns:yang:sr:state:chassis").build(),
        Schema.builder().element("<cpm />").namespace("urn:nokia.com:sros:ns:yang:sr:state:cpm").build(),
        Schema.builder().element("<esa />").namespace("urn:nokia.com:sros:ns:yang:sr:state:esa").build(),
        Schema.builder().element("<eth-cfm />").namespace("urn:nokia.com:sros:ns:yang:sr:state:eth-cfm").build(),
        Schema.builder().element("<eth-ring />").namespace("urn:nokia.com:sros:ns:yang:sr:state:eth-ring").build(),
        Schema.builder().element("<filter />").namespace("urn:nokia.com:sros:ns:yang:sr:state:filter").build(),
        Schema.builder().element("<fwd-path-ext />").namespace("urn:nokia.com:sros:ns:yang:sr:state:fwd-path-ext").build(),
        Schema.builder().element("<group-encryption />").namespace("urn:nokia.com:sros:ns:yang:sr:state:group-encryption").build(),
        Schema.builder().element("<ipsec />").namespace("urn:nokia.com:sros:ns:yang:sr:state:ipsec").build(),
        Schema.builder().element("<isa />").namespace("urn:nokia.com:sros:ns:yang:sr:state:isa").build(),
        Schema.builder().element("<lag />").namespace("urn:nokia.com:sros:ns:yang:sr:state:lag").build(),
        Schema.builder().element("<log />").namespace("urn:nokia.com:sros:ns:yang:sr:state:log").build(),
        Schema.builder().element("<macsec />").namespace("urn:nokia.com:sros:ns:yang:sr:state:macsec").build(),
        Schema.builder().element("<mcac />").namespace("urn:nokia.com:sros:ns:yang:sr:state:mcac").build(),
        Schema.builder().element("<mirror />").namespace("urn:nokia.com:sros:ns:yang:sr:state:mirror").build(),
        Schema.builder().element("<multicast-management />").namespace("urn:nokia.com:sros:ns:yang:sr:state:multicast-management").build(),
        Schema.builder().element("<multilink-bundle />").namespace("urn:nokia.com:sros:ns:yang:sr:state:multilink-bundle").build(),
        Schema.builder().element("<mvpn-extranet />").namespace("urn:nokia.com:sros:ns:yang:sr:state:mvpn-extranet").build(),
        Schema.builder().element("<oam-pm />").namespace("urn:nokia.com:sros:ns:yang:sr:state:oam-pm").build(),
        Schema.builder().element("<openflow />").namespace("urn:nokia.com:sros:ns:yang:sr:state:openflow").build(),
        Schema.builder().element("<policy-options />").namespace("urn:nokia.com:sros:ns:yang:sr:state:policy-options").build(),
        Schema.builder().element("<port />").namespace("urn:nokia.com:sros:ns:yang:sr:state:port").build(),
        Schema.builder().element("<port-xc />").namespace("urn:nokia.com:sros:ns:yang:sr:state:port-xc").build(),
        Schema.builder().element("<pw-port />").namespace("urn:nokia.com:sros:ns:yang:sr:state:pw-port").build(),
        Schema.builder().element("<python />").namespace("urn:nokia.com:sros:ns:yang:sr:state:python").build(),
        Schema.builder().element("<qos />").namespace("urn:nokia.com:sros:ns:yang:sr:state:qos").build(),
        Schema.builder().element("<redundancy />").namespace("urn:nokia.com:sros:ns:yang:sr:state:redundancy").build(),
        // We specify all the YANG subtrees of <router> we want while avoiding <route-table/> and <bgp/>.
        Schema.builder().element(
                "\n  <router>\n" +
                    "    <router-name/>\n" +
                    "    <vrtr-id/>\n" +
                    "    <oper-router-id/>\n" +
                    "    <gtp/>\n" +
                    "    <aggregates/>\n" +
                    "    <wlan-gw-tunnel/>\n" +
                    "    <sfm-overload/>\n" +
                    "    <interface/>\n" +
                    "    <ipv4/>\n" +
                    "    <ipv6/>\n" +
                    "    <tunnel-interface/>\n" +
                    "    <pcp/>\n" +
                    "    <tunnel-table/>\n" +
                    "    <network-domains/>\n" +
                    "    <dhcp6/>\n" +
                    "    <bier/>\n" +
                    "    <dhcp-server/>\n" +
                    "    <igmp/>\n" +
                    "    <isis/>\n" +
                    "    <l2tp/>\n" +
                    "    <label-fib/>\n" +
                    "    <ldp/>\n" +
                    "    <mld/>\n" +
                    "    <mpls/>\n" +
                    "    <msdp/>\n" +
                    "    <nat/>\n" +
                    "    <origin-validation/>\n" +
                    "    <ospf/>\n" +
                    "    <ospf3/>\n" +
                    "    <p2mp-sr-tree/>\n" +
                    "    <pcep/>\n" +
                    "    <pim/>\n" +
                    "    <radius/>\n" +
                    "    <rib-api/>\n" +
                    "    <rip/>\n" +
                    "    <ripng/>\n" +
                    "    <route-fib/>\n" +
                    "    <rsvp/>\n" +
                    "    <segment-routing/>\n" +
                    "    <static-routes/>\n" +
                    "    <tunnel-fib/>\n" +
                    "    <twamp-light/>\n" +
                    "    <wpp/>\n" +
                    "  </router>\n")
            .namespace("urn:nokia.com:sros:ns:yang:sr:state:router").build(),
        Schema.builder().element("<satellite />").namespace("urn:nokia.com:sros:ns:yang:sr:state:satellite").build(),
        Schema.builder().element("<service />").namespace("urn:nokia.com:sros:ns:yang:sr:state:service").build(),
        Schema.builder().element("<sfm />").namespace("urn:nokia.com:sros:ns:yang:sr:state:sfm").build(),
        Schema.builder().element("<subscriber-mgmt />").namespace("urn:nokia.com:sros:ns:yang:sr:state:subscriber-mgmt").build(),
        Schema.builder().element("<system />").namespace("urn:nokia.com:sros:ns:yang:sr:state:system").build(),
        Schema.builder().element("<test-oam />").namespace("urn:nokia.com:sros:ns:yang:sr:state:test-oam").build(),
        Schema.builder().element("<users />").namespace("urn:nokia.com:sros:ns:yang:sr:state:users").build(),
        Schema.builder().element("<vrrp />").namespace("urn:nokia.com:sros:ns:yang:sr:state:vrrp").build()
        /**
         * These two YANG subtrees are too large for the Nokia to return and will timeout.
         */
        /*Schema.builder().element(
                "\n  <router>\n" +
                    "    <route-table/>\n" +
                    "  </router>\n")
            .namespace("urn:nokia.com:sros:ns:yang:sr:state:router:route-table").build(),
        Schema.builder().element(
                "\n  <router>\n" +
                    "    <bgp/>\n" +
                    "  </router>\n")
            .namespace("urn:nokia.com:sros:ns:yang:sr:state:router:bgp").build()*/
    );
}