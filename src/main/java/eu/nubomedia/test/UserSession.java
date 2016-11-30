/*
 * (C) Copyright 2016 NUBOMEDIA (http://www.nubomedia.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package eu.nubomedia.test;

import org.kurento.client.ConnectionStateChangedEvent;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.OnIceCandidateEvent;
import org.kurento.client.Properties;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

/**
 * User session.
 * 
 * @author Boni Garcia (boni.garcia@urjc.es)
 * @since 6.6.1
 */
public class UserSession {

  private final Logger log = LoggerFactory.getLogger(UserSession.class);

  private Handler handler;
  private WebRtcEndpoint webRtcEndpoint1;
  private WebRtcEndpoint webRtcEndpoint2;
  private KurentoClient kurentoClient1;
  private KurentoClient kurentoClient2;
  private MediaPipeline mediaPipeline1;
  private MediaPipeline mediaPipeline2;
  private String sessionId;

  public UserSession(String sessionId, Handler handler) {
    this.sessionId = sessionId;
    this.handler = handler;
  }

  public String startPresenter(final WebSocketSession session, String sdpOffer, int loadPoints) {
    // One KurentoClient instance per session (reserving points per session)
    Properties properties = new Properties();
    properties.add("loadPoints", loadPoints);
    kurentoClient1 = KurentoClient.create(properties);
    log.info("Created kurentoClient1 (session {}, points {})", sessionId, loadPoints);

    // Media logic (pipeline and media elements connectivity)
    mediaPipeline1 = kurentoClient1.createMediaPipeline();
    log.info("Created Media Pipeline 1 {} (session {})", mediaPipeline1.getId(), sessionId);

    webRtcEndpoint1 = new WebRtcEndpoint.Builder(mediaPipeline1).build();

    // WebRTC negotiation
    webRtcEndpoint1.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
      @Override
      public void onEvent(OnIceCandidateEvent event) {
        JsonObject response = new JsonObject();
        response.addProperty("id", "iceCandidate1");
        response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
        handler.sendMessage(session, new TextMessage(response.toString()));
      }
    });
    String sdpAnswer = webRtcEndpoint1.processOffer(sdpOffer);
    webRtcEndpoint1.gatherCandidates();

    return sdpAnswer;
  }

  public String startViewer(final WebSocketSession session, String sdpOffer, int loadPoints) {
    // One KurentoClient instance per session (reserving points per session)
    Properties properties = new Properties();
    properties.add("loadPoints", loadPoints);
    kurentoClient2 = KurentoClient.create(properties);
    log.info("Created kurentoClient2 (session {}, points {})", sessionId, loadPoints);

    // Media logic (pipeline and media elements connectivity)
    mediaPipeline2 = kurentoClient2.createMediaPipeline();
    log.info("Created Media Pipeline 2 {} (session {})", mediaPipeline2.getId(), sessionId);

    WebRtcEndpoint webRtcEndpoint1_2 = new WebRtcEndpoint.Builder(mediaPipeline1).build();
    webRtcEndpoint1.connect(webRtcEndpoint1_2);

    WebRtcEndpoint webRtcEndpoint2_2 = new WebRtcEndpoint.Builder(mediaPipeline2).build();
    webRtcEndpoint2 = new WebRtcEndpoint.Builder(mediaPipeline2).build();
    connectWebRtcEndpoints(webRtcEndpoint1_2, webRtcEndpoint2_2);
    webRtcEndpoint2_2.connect(webRtcEndpoint2);

    webRtcEndpoint2.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
      @Override
      public void onEvent(OnIceCandidateEvent event) {
        JsonObject response = new JsonObject();
        response.addProperty("id", "iceCandidate2");
        response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
        handler.sendMessage(session, new TextMessage(response.toString()));
      }
    });
    String sdpAnswer = webRtcEndpoint2.processOffer(sdpOffer);
    webRtcEndpoint2.gatherCandidates();

    return sdpAnswer;
  }

  public void addCandidate1(JsonObject jsonCandidate) {
    IceCandidate candidate = new IceCandidate(jsonCandidate.get("candidate").getAsString(),
        jsonCandidate.get("sdpMid").getAsString(), jsonCandidate.get("sdpMLineIndex").getAsInt());
    webRtcEndpoint1.addIceCandidate(candidate);
  }

  public void addCandidate2(JsonObject jsonCandidate) {
    IceCandidate candidate = new IceCandidate(jsonCandidate.get("candidate").getAsString(),
        jsonCandidate.get("sdpMid").getAsString(), jsonCandidate.get("sdpMLineIndex").getAsInt());
    webRtcEndpoint2.addIceCandidate(candidate);
  }

  public void release() {
    log.info("Releasing media pipeline 1 {} (session {})", mediaPipeline1.getId(), sessionId);
    mediaPipeline1.release();

    log.info("Destroying kurentoClient 1 (session {})", sessionId);
    kurentoClient1.destroy();

    log.info("Releasing media pipeline 2 {} (session {})", mediaPipeline1.getId(), sessionId);
    mediaPipeline2.release();

    log.info("Destroying kurentoClient 2 (session {})", sessionId);
    kurentoClient2.destroy();
  }

  private void connectWebRtcEndpoints(final WebRtcEndpoint senderWebRtcEndpoint,
      final WebRtcEndpoint receiverWebRtcEndpoint) {

    final String senderId = senderWebRtcEndpoint.getId();
    final String receiverId = receiverWebRtcEndpoint.getId();

    log.info("WebRtcEndpoint {} is acting as SENDER (media pipeline {})", senderId,
        senderWebRtcEndpoint.getMediaPipeline().getId());
    log.info("WebRtcEndpoint {} is acting as RECEIVER (media pipeline {})", receiverId,
        receiverWebRtcEndpoint.getMediaPipeline().getId());

    senderWebRtcEndpoint
        .addConnectionStateChangedListener(new EventListener<ConnectionStateChangedEvent>() {
          @Override
          public void onEvent(ConnectionStateChangedEvent event) {
            log.info("State changed in SENDER WebRtcEndpoint: {}->{} at {}", event.getOldState(),
                event.getNewState(), event.getTimestamp());
          }
        });

    receiverWebRtcEndpoint
        .addConnectionStateChangedListener(new EventListener<ConnectionStateChangedEvent>() {
          @Override
          public void onEvent(ConnectionStateChangedEvent event) {
            log.info("State changed in RECEIVER WebRtcEndpoint: {}->{} at {}", event.getOldState(),
                event.getNewState(), event.getTimestamp());
          }
        });

    senderWebRtcEndpoint.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
      @Override
      public void onEvent(OnIceCandidateEvent event) {
        IceCandidate candidate = event.getCandidate();
        log.info("ICE candidate on SENDER WebRtcEndpoint {} --> {}", senderId, candidate);
        receiverWebRtcEndpoint.addIceCandidate(candidate);
      }
    });

    receiverWebRtcEndpoint.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
      @Override
      public void onEvent(OnIceCandidateEvent event) {
        IceCandidate candidate = event.getCandidate();
        log.info("ICE candidate on RECEIVER WebRtcEndpoint {} --> {}", receiverId, candidate);
        senderWebRtcEndpoint.addIceCandidate(candidate);
      }
    });

    String sdpOffer = receiverWebRtcEndpoint.generateOffer();
    String sdpAnswer = senderWebRtcEndpoint.processOffer(sdpOffer);
    receiverWebRtcEndpoint.processAnswer(sdpAnswer);

    log.info("SDP offer generated by RECEIVER WebRtcEndpoint {} --> {}", receiverId, sdpOffer);
    log.info("SDP answer generated by SENDER WebRtcEndpoint {} --> {}", senderId, sdpAnswer);

    senderWebRtcEndpoint.gatherCandidates();
    receiverWebRtcEndpoint.gatherCandidates();
  }

}
