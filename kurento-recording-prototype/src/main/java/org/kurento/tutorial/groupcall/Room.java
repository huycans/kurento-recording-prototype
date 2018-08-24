/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
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

package org.kurento.tutorial.groupcall;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Date;

import javax.annotation.PreDestroy;

import org.kurento.client.Continuation;
import org.kurento.client.ErrorEvent;
import org.kurento.client.EventListener;
import org.kurento.client.MediaPipeline;
import org.kurento.client.Composite;
import org.kurento.client.HubPort;
import org.kurento.client.MediaType;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.MediaFlowOutStateChangeEvent;
import org.kurento.client.MediaFlowState;
import org.kurento.client.MediaProfileSpecType;
import org.kurento.client.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class Room implements Closeable {
  private final Logger log = LoggerFactory.getLogger(Room.class);

  private final ConcurrentMap<String, UserSession> participants = new ConcurrentHashMap<>();
  private final MediaPipeline pipeline;
  private final String name;
  private final Composite composite;
  private final RecorderEndpoint recorderEndpoint;
  private Boolean isRecording = false;
  public String getName() {
    return name;
  }

  public Room(String roomName, MediaPipeline pipeline) {
    this.name = roomName;
    this.pipeline = pipeline;
    this.composite = new Composite.Builder(pipeline).build();

    //setup date formatter
    final Date date = new java.util.Date();

    //initialize recorder endpoint for recording
    this.recorderEndpoint =  new RecorderEndpoint
    .Builder(pipeline,"file:///tmp/" + roomName + "-" + date.toString() + ".webm")
    .withMediaProfile(MediaProfileSpecType.WEBM)
    .build();
    //bitrate is bps
    this.recorderEndpoint.setMaxOutputBitrate(100000);

    this.composite.addErrorListener(new EventListener<ErrorEvent>(){
    
      @Override
      public void onEvent(ErrorEvent event) {
        log.warn("Error with composite element", event);
      }
    });

    log.info("ROOM {} has been created", roomName);
  }

  @PreDestroy
  private void shutdown() {
    this.close();
  }
  
  public UserSession join(String userName, WebSocketSession session) throws IOException {
    log.info("ROOM {}: adding participant {}", userName, userName);

    //mark the code
    HubPort hubport = new HubPort.Builder(composite).build();
    final UserSession participant = new UserSession(userName, this.name, session, this.pipeline, hubport, this);
    joinRoom(participant);
    participants.put(participant.getName(), participant);
    sendParticipantNames(participant);
    
    return participant;
  }

  public void leave(UserSession user) throws IOException {
    log.debug("PARTICIPANT {}: Leaving room {}", user.getName(), this.name);
    this.removeParticipant(user.getName());
    user.close();
  }

  private Collection<String> joinRoom(UserSession newParticipant) throws IOException {
    final JsonObject newParticipantMsg = new JsonObject();
    newParticipantMsg.addProperty("id", "newParticipantArrived");
    newParticipantMsg.addProperty("name", newParticipant.getName());

    final List<String> participantsList = new ArrayList<>(participants.values().size());
    log.debug("ROOM {}: notifying other participants of new participant {}", name,
        newParticipant.getName());

    for (final UserSession participant : participants.values()) {
      try {
        participant.sendMessage(newParticipantMsg);
      } catch (final IOException e) {
        log.debug("ROOM {}: participant {} could not be notified", name, participant.getName(), e);
      }
      participantsList.add(participant.getName());
    }

    return participantsList;
  }

  private void removeParticipant(String name) throws IOException {
    participants.remove(name);

    log.debug("ROOM {}: notifying all users that {} is leaving the room", this.name, name);

    final List<String> unnotifiedParticipants = new ArrayList<>();
    final JsonObject participantLeftJson = new JsonObject();
    participantLeftJson.addProperty("id", "participantLeft");
    participantLeftJson.addProperty("name", name);
    for (final UserSession participant : participants.values()) {
      try {
        participant.cancelVideoFrom(name);
        participant.sendMessage(participantLeftJson);
      } catch (final IOException e) {
        unnotifiedParticipants.add(participant.getName());
      }
    }

    if (!unnotifiedParticipants.isEmpty()) {
      log.debug("ROOM {}: The users {} could not be notified that {} left the room", this.name,
          unnotifiedParticipants, name);
    }

  }

  public void sendParticipantNames(UserSession user) throws IOException {

    final JsonArray participantsArray = new JsonArray();
    for (final UserSession participant : this.getParticipants()) {
      if (!participant.equals(user)) {
        final JsonElement participantName = new JsonPrimitive(participant.getName());
        participantsArray.add(participantName);
      }
    }

    final JsonObject existingParticipantsMsg = new JsonObject();
    existingParticipantsMsg.addProperty("id", "existingParticipants");
    existingParticipantsMsg.add("data", participantsArray);
    log.debug("PARTICIPANT {}: sending a list of {} participants", user.getName(),
        participantsArray.size());
    user.sendMessage(existingParticipantsMsg);
  }

  public Collection<UserSession> getParticipants() {
    return participants.values();
  }

  public Composite getComposite(){
    return this.composite;
  }

  public RecorderEndpoint getRecorder(){
    return this.recorderEndpoint;
  }

  public UserSession getParticipant(String name) {
    return participants.get(name);
  }

  public void startRecording(){
    if (isRecording == false){
      this.recorderEndpoint.record();
      this.isRecording = true;
    }
  }
  @Override
  public void close() {
    if (recorderEndpoint != null){
      recorderEndpoint.stopAndWait();
    }

    if (composite != null){
      composite.release();
    }

    for (final UserSession user : participants.values()) {
      try {
        user.close();
      } catch (IOException e) {
        log.debug("ROOM {}: Could not invoke close on participant {}", name, user.getName(),
            e);
      }
    }

    participants.clear();

    pipeline.release(new Continuation<Void>() {

      @Override
      public void onSuccess(Void result) throws Exception {
        log.trace("ROOM {}: Released Pipeline", Room.this.name);
      }

      @Override
      public void onError(Throwable cause) throws Exception {
        log.warn("PARTICIPANT {}: Could not release Pipeline", Room.this.name);
      }
    });

    composite.release(new Continuation<Void>() {

      @Override
      public void onSuccess(Void result) throws Exception {
        log.trace("ROOM {}: Released composite", Room.this.name);
      }

      @Override
      public void onError(Throwable cause) throws Exception {
        log.warn("PARTICIPANT {}: Could not release composite", Room.this.name);
      }
    });

    log.debug("Room {} closed", name);
    
  }

}
