package net.es.netapps.netconf;

import lombok.Data;
import net.juniper.netconf.Device;
import net.juniper.netconf.NetconfException;
import net.juniper.netconf.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.util.List;

/**
 * A generalization for a discoverable NETCONF device.
 */
@Data
public class Discoverable {
  private static final Logger logger = LoggerFactory.getLogger(Discoverable.class);
  private final String hostname;
  private final String username;
  private final String password;
  private final long timeout;

  // Device that controls netconf communications.
  private Device device = null;

  /**
   * Initialize all parameters needed to perform discovery on a device.
   *
   * @param hostname
   * @param username
   * @param password
   * @param timeout
   */
  public Discoverable(String hostname, String username, String password, long timeout) {
    logger.info("[Discoverable] starting...");
    this.hostname = hostname;
    this.username = username;
    this.password = password;
    this.timeout = timeout;
  }

  /**
   * Create a NETCONF session to the device.
   *
   * @throws NetconfException
   */
  public void connect() throws NetconfException {
    logger.info("[Discoverable] connecting ...");

    try {
      device = Device.builder()
          .hostName(hostname)
          .userName(username)
          .password(password)
          .strictHostKeyChecking(false)
          .commandTimeout(5 * 60 * 1000) // 5 minutes to account for large transponder responses.
          .build();
      device.connect();
    } catch (NetconfException ex) {
      logger.error("[Discoverable] {} failed to connect", hostname, ex);
      throw ex;
    }

    logger.info("[Discoverable] connected to {}.", hostname);
  }

  /**
   * Disconnect NETCONF session to device.
   *
   * @throws NetconfException
   */
  public void disconnect() throws NetconfException {
    logger.info("[Discoverable] disconnecting ...");
    if (device == null) {
      logger.error("[Discoverable] {} not connected", hostname);
      throw new NetconfException("Device is not connected.");
    }

    device.close();
    logger.info("[Discoverable] terminated connection to {}.", hostname);
  }

  /**
   * Get the NETCONF capabilities of the connected device.
   *
   * @return
   */
  public List<String> getCapabilities() throws NetconfException {
    logger.info("[Discoverable] getting capabilities ...");
    if (device == null) {
      logger.error("[Discoverable] {} not connected", hostname);
      throw new NetconfException("Device is not connected");
    }

    // Discovery capabilities from the device.
    return device.getNetconfSession().getServerHello().getCapabilities();
  }

  /**
   * Get the running configuration and state from the NETCONF device.
   *
   * @param schema
   * @return
   */
  public String getRunningConfigAndState(String element, String schema) throws NetconfException {
    try {
      logger.debug("[Discoverable] getting running configuration and state for {} from {}", element, hostname);
      XML result = device.getRunningConfigAndState(schema);

      if (device.hasError()) {
        device.getNetconfSession().getLastRpcReplyObject().getErrors().forEach(e -> {
          logger.error("[Discoverable] encountered error \"{}\", {} = \"{}\"",
              e.getErrorMessage(), e.getErrorInfo().getType(), e.getErrorInfo().getValue());
        });
        throw new NetconfException(
            String.format("Failed to retrieve running configuration and state for %s from %s", element, hostname));
      }

      logger.debug("[Discoverable] parsing {} document from {}", element, hostname);
      return parseState(result.getOwnerDocument());
    } catch (IOException | SAXException | XPathExpressionException ex) {
      logger.error("[Discoverable] {} could not get running configuration and state {}",
          hostname, schema, ex);
      throw new NetconfException(ex.getMessage());
    }
  }

  /**
   * Get state information from the NETCONF RPC message.
   *
   * @param doc
   * @return
   * @throws XPathExpressionException
   */
  public static String parseState(Document doc) throws XPathExpressionException {
    XPath xPath = XPathFactory.newInstance().newXPath();
    NodeList nodes = (NodeList) xPath.evaluate("/rpc-reply/data/state", doc, XPathConstants.NODESET);
    if (nodes == null) {
      logger.info("[Discoverable] empty state returned.");
      return null;
    }

    return XML.getNodeString(nodes.item(0), 4, true);
  }
}
