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

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.internal.NotEnoughResourcesException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Handler.
 *
 * @author Boni Garcia (boni.garcia@urjc.es)
 * @since 6.6.1
 */
public class Handler extends TextWebSocketHandler {

  private final Logger log = LoggerFactory.getLogger(Handler.class);

  private final ConcurrentHashMap<String, UserSession> users = new ConcurrentHashMap<>();

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    try {
      JsonObject jsonMessage =
          new GsonBuilder().create().fromJson(message.getPayload(), JsonObject.class);

      log.info("Incoming message: {}", jsonMessage);

      switch (jsonMessage.get("id").getAsString()) {
        case "startPresenter":
          startPresenter(session, jsonMessage);
          break;
        case "startViewer":
          startViewer(session, jsonMessage);
          break;
        case "stop": {
          release(session);
          break;
        }
        case "onIceCandidate1": {
          onIceCandidate1(session, jsonMessage);
          break;
        }
        case "onIceCandidate2": {
          onIceCandidate2(session, jsonMessage);
          break;
        }
        default:
          error(session, "Invalid message with id " + jsonMessage.get("id").getAsString());
          break;
      }

    } catch (NotEnoughResourcesException e) {
      log.warn("Not enough resources", e);
      notEnoughResources(session);

    } catch (Throwable t) {
      log.error("Exception starting session", t);
      error(session, t.getClass().getSimpleName() + ": " + t.getMessage());
    }
  }

  private void startPresenter(final WebSocketSession session, JsonObject jsonMessage) {
    // User session
    String sessionId = session.getId();
    UserSession user = new UserSession(sessionId, this);
    users.put(sessionId, user);

    // Media logic
    String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
    int loadPoints = jsonMessage.get("loadPoints").getAsInt();
    String sdpAnswer = user.startPresenter(session, sdpOffer, loadPoints);

    // Response message
    JsonObject response = new JsonObject();
    response.addProperty("id", "presenterResponse");
    response.addProperty("sdpAnswer", sdpAnswer);
    sendMessage(session, new TextMessage(response.toString()));
  }

  private void startViewer(final WebSocketSession session, JsonObject jsonMessage) {
    // User session
    String sessionId = session.getId();
    UserSession user = users.get(sessionId);

    // Media logic
    String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
    int loadPoints = jsonMessage.get("loadPoints").getAsInt();
    String sdpAnswer = user.startViewer(session, sdpOffer, loadPoints);

    // Response message
    JsonObject response = new JsonObject();
    response.addProperty("id", "viewerResponse");
    response.addProperty("sdpAnswer", sdpAnswer);
    sendMessage(session, new TextMessage(response.toString()));
  }

  private void onIceCandidate1(WebSocketSession session, JsonObject jsonMessage) {
    JsonObject jsonCandidate = jsonMessage.get("candidate").getAsJsonObject();
    UserSession user = users.get(session.getId());
    if (user != null) {
      user.addCandidate1(jsonCandidate);
    }
  }

  private void onIceCandidate2(WebSocketSession session, JsonObject jsonMessage) {
    JsonObject jsonCandidate = jsonMessage.get("candidate").getAsJsonObject();
    UserSession user = users.get(session.getId());
    if (user != null) {
      user.addCandidate2(jsonCandidate);
    }
  }

  private void notEnoughResources(WebSocketSession session) {
    // Send notEnoughResources message to client
    JsonObject response = new JsonObject();
    response.addProperty("id", "notEnoughResources");
    sendMessage(session, new TextMessage(response.toString()));

    // Release media session
    release(session);
  }

  private void error(WebSocketSession session, String message) {
    // Send error message to client
    JsonObject response = new JsonObject();
    response.addProperty("id", "error");
    response.addProperty("message", message);
    sendMessage(session, new TextMessage(response.toString()));

    // Release media session
    release(session);
  }

  private void release(WebSocketSession session) {
    UserSession user = users.remove(session.getId());
    if (user != null) {
      user.release();
    }
  }

  public synchronized void sendMessage(WebSocketSession session, TextMessage message) {
    try {
      log.info("Sending message {} in session {}", message.getPayload(), session.getId());
      session.sendMessage(message);

    } catch (IOException e) {
      log.error("Exception sending message", e);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    log.info("Closed websocket connection of session {}", session.getId());
    release(session);
  }
}
