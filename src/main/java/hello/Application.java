package hello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

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
//    int desiredWidth = arenaUpdate.arena.dims.get(0) / 2;
//    int desiredHeight = arenaUpdate.arena.dims.get(1) / 2;
    PlayerState myState = getMyState(arenaUpdate);
      if (!myState.wasHit && isAnybodyWithinTheShotRange(arenaUpdate)) {
          System.out.println("throwing");
          return "T";
      } else {
          String[] moves = new String[] {"R", "F"};
          int rand = new Random().nextInt(2);
          String move = moves[rand];
          System.out.println("moving: " + move);
          return move;
      }
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

  private boolean amIInPosition(PlayerState myState, int width, int height) {
    return myState.x == width && myState.y == height;
  }

  private String moveTo(PlayerState myState, int width, int height) {
    String desiredDirection = chooseMoveDirection(myState, width, height);
    if (!myState.direction.equals(desiredDirection)) {
      return "R";
    } else {
      return "F";
    }
  }

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
}

