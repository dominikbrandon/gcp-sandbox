package hello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import com.google.api.core.ApiFuture;
import com.google.cloud.ServiceOptions;
import com.google.cloud.bigquery.storage.v1.*;
import com.google.protobuf.Descriptors;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;


@SpringBootApplication
@RestController
public class Application {

  static class Self {
    public String href;
  }

  static class Links {
    public Self self;
  }

  static class PlayerState {
    public Integer x;
    public Integer y;
    public String direction;
    public Boolean wasHit;
    public Integer score;
  }

  static class Arena {
    public List<Integer> dims;
    public Map<String, PlayerState> state;
  }

  static class ArenaUpdate {
    public Links _links;
    public Arena arena;
  }

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @InitBinder
  public void initBinder(WebDataBinder binder) {
    binder.initDirectFieldAccess();
  }

  @GetMapping("/")
  public String index() {
    return "Let the battle begin!";
  }

  @PostMapping("/**")
  public String index(@RequestBody ArenaUpdate arenaUpdate) {
    System.out.println(arenaUpdate);
    writeCommittedStream.send(arenaUpdate.arena);
    int centerWidth = arenaUpdate.arena.dims.get(0) / 2;
    int centerHeight = arenaUpdate.arena.dims.get(1) / 2;
    PlayerState myState = getMyState(arenaUpdate);
      if (!myState.wasHit && isAnybodyWithinTheShotRange(arenaUpdate)) {
          System.out.println("throwing");
          return "T";
      } else {
          int rand = new Random().nextInt(5);
          if (canGoForward(arenaUpdate) && rand != 0) {
              System.out.println("going forward");
              return "F";
          } else {
              String rotationCommand = rotate(arenaUpdate);
              System.out.println("turning..." + rotationCommand);
              return rotationCommand;
          }
//          String[] moves = new String[] {"R", "F"};
//          int rand = new Random().nextInt(2);
//          String move = moves[rand];
//          System.out.println("moving: " + move);
//          return move;
      }
  }

  private String rotate(ArenaUpdate currentState) {
      PlayerState myState = getMyState(currentState);
      int centerWidth = currentState.arena.dims.get(0) / 2;
      int centerHeight = currentState.arena.dims.get(1) / 2;
      String desiredDirection = chooseMoveDirection(myState, centerWidth, centerHeight);
      String myCurrentDirection = myState.direction;
      if (
              myCurrentDirection.equals(desiredDirection)
                      || ("N".equals(desiredDirection) && "E".equals(myCurrentDirection))
                      || ("E".equals(desiredDirection) && "S".equals(myCurrentDirection))
                      || ("S".equals(desiredDirection) && "W".equals(myCurrentDirection))
                      || ("W".equals(desiredDirection) && "N".equals(myCurrentDirection))
      ) {
          return "L";
      } else {
          return "R";
      }
  }

  private boolean canGoForward(ArenaUpdate currentState) {
      PlayerState myState = getMyState(currentState);
      int forwardFieldWidth = myState.x;
      int forwardFieldHeight = myState.y;
      if (myState.direction.equals("N")) {
          forwardFieldHeight--;
      } else if (myState.direction.equals("E")) {
          forwardFieldWidth++;
      } else if (myState.direction.equals("S")) {
          forwardFieldHeight++;
      } else {
          forwardFieldWidth--;
      }
      if (forwardFieldWidth < 0 || forwardFieldHeight < 0 || forwardFieldWidth >= currentState.arena.dims.get(0) || forwardFieldHeight >= currentState.arena.dims.get(1)) {
          return false;
      }
      int finalForwardFieldWidth = forwardFieldWidth;
      int finalForwardFieldHeight = forwardFieldHeight;
      return currentState.arena.state.values()
              .stream()
              .noneMatch(player -> player.x == finalForwardFieldWidth && player.y == finalForwardFieldHeight);
  }

  private boolean isAnybodyWithinTheShotRange(ArenaUpdate currentState) {
      PlayerState myState = getMyState(currentState);
      int myWidth = myState.x;
      int myHeight = myState.y;
      String myDirection = myState.direction;
      Predicate<PlayerState> isWithin;
      if (myDirection.equals("N")) {
          isWithin = player -> player.x == myWidth && player.y < myHeight && player.y >= myHeight - 3;
      } else if (myDirection.equals("E")) {
          isWithin = player -> player.y == myHeight && player.x > myWidth && player.x <= myWidth + 3;
      } else if (myDirection.equals("S")) {
          isWithin = player -> player.x == myWidth && player.y > myHeight && player.y <= myHeight + 3;
      } else {
          isWithin = player -> player.y == myHeight && player.x < myWidth && player.x >= myWidth - 3;
      }
      return currentState.arena.state.values()
              .stream()
              .anyMatch(isWithin);
  }

//  private boolean amIInPosition(PlayerState myState, int width, int height) {
//    return myState.x == width && myState.y == height;
//  }
//
//  private String moveTowards(PlayerState myState, int width, int height) {
//    String desiredDirection = chooseMoveDirection(myState, width, height);
//      String myCurrentDirection = myState.direction;
//      if (!myCurrentDirection.equals(desiredDirection)) {
//          if (
//                  ("N".equals(desiredDirection) && "E".equals(myCurrentDirection))
//                  || ("E".equals(desiredDirection) && "S".equals(myCurrentDirection))
//                  || ("S".equals(desiredDirection) && "W".equals(myCurrentDirection))
//                  || ("W".equals(desiredDirection) && "N".equals(myCurrentDirection))
//          ) {
//            return "L";
//          } else {
//              return "R";
//          }
//      } else {
//          return "F";
//    }
//  }

  private String chooseMoveDirection(PlayerState myState, int desiredWidth, int desiredHeight) {
    if (desiredWidth < myState.x) {
      return "W";
    } else if (desiredWidth > myState.x) {
      return "E";
    }
    if (desiredHeight < myState.y) {
      return "N";
    } else {
      return "S";
    }
  }

  private PlayerState getMyState(ArenaUpdate currentState) {
    String myUrl = currentState._links.self.href;
    return currentState.arena.state.get(myUrl);
  }

  static class WriteCommittedStream {

        final JsonStreamWriter jsonStreamWriter;

        public WriteCommittedStream(String projectId, String datasetName, String tableName) throws IOException, Descriptors.DescriptorValidationException, InterruptedException {

            try (BigQueryWriteClient client = BigQueryWriteClient.create()) {

                WriteStream stream = WriteStream.newBuilder().setType(WriteStream.Type.COMMITTED).build();
                TableName parentTable = TableName.of(projectId, datasetName, tableName);
                CreateWriteStreamRequest createWriteStreamRequest =
                        CreateWriteStreamRequest.newBuilder()
                                .setParent(parentTable.toString())
                                .setWriteStream(stream)
                                .build();

                WriteStream writeStream = client.createWriteStream(createWriteStreamRequest);

                jsonStreamWriter = JsonStreamWriter.newBuilder(writeStream.getName(), writeStream.getTableSchema()).build();
            }
        }

        public ApiFuture<AppendRowsResponse> send(Arena arena) {
            Instant now = Instant.now();
            JSONArray jsonArray = new JSONArray();

            arena.state.forEach((url, playerState) -> {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("x", playerState.x);
                jsonObject.put("y", playerState.y);
                jsonObject.put("direction", playerState.direction);
                jsonObject.put("wasHit", playerState.wasHit);
                jsonObject.put("score", playerState.score);
                jsonObject.put("player", url);
                jsonObject.put("timestamp", now.getEpochSecond() * 1000 * 1000);
                jsonArray.put(jsonObject);
            });

            return jsonStreamWriter.append(jsonArray);
        }

    }

    final String projectId = ServiceOptions.getDefaultProjectId();
    final String datasetName = "snowball";
    final String tableName = "events";

    final WriteCommittedStream writeCommittedStream;

    public Application() throws Descriptors.DescriptorValidationException, IOException, InterruptedException {
        writeCommittedStream = new WriteCommittedStream(projectId, datasetName, tableName);
    }
}

