package us.terracraft.multiplayertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import us.terracraft.multiplayertest.sprites.Starship;

import java.util.HashMap;

public class MultiplayerTest extends ApplicationAdapter {

    private final float UPDATE_TIME = 1/60f;

    float timer;

	SpriteBatch batch;
	private Socket socket;

	Starship player;
	Texture playerShip;
	Texture friendlyShip;

	HashMap<String, Starship> friendlyPlayers;

	@Override
	public void create () {
        Gdx.graphics.setWindowedMode(854 * 2, 480 * 2);
		batch = new SpriteBatch();
        playerShip = new Texture("playerShip2.png");
        friendlyShip = new Texture("playerShip.png");
        friendlyPlayers = new HashMap<String, Starship>();
		connectSocket();
		configSocketEvents();
	}

	public void handleInput(float dt) {
	    if (player != null) {
	        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
	            player.setPosition(player.getX() + (-200 * dt), player.getY());
            } else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
                player.setPosition(player.getX() + (+200 * dt), player.getY());
            } else if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
                player.setPosition(player.getX(), player.getY() + (+200 * dt));
            } else if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
                player.setPosition(player.getX(), player.getY() + (-200 * dt));
            }
        }
    }

    public void updateServer(float dt) {
        timer += dt;
        if (timer >= UPDATE_TIME && player != null && player.hasMoved()) {
            JSONObject data = new JSONObject();
            try {
                data.put("x", player.getX());
                data.put("y", player.getY());
                socket.emit("playerMoved", data);
            } catch (JSONException e) {
                Gdx.app.log("SocketIO", "Error sending update Data");
            }
        }
    }

	@Override
	public void render() {

        handleInput(Gdx.graphics.getDeltaTime());
        updateServer(Gdx.graphics.getDeltaTime());

		Gdx.gl.glClearColor(0.4f, 0.5f, 1, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		batch.begin();
		if (player != null) {
		    player.draw(batch);
        }

		for (HashMap.Entry<String, Starship> entry: friendlyPlayers.entrySet()) {
		    entry.getValue().draw(batch);
        }

		batch.end();
	}
	
	@Override
	public void dispose() {
		batch.dispose();
        playerShip.dispose();
        friendlyShip.dispose();
	}

	public void connectSocket() {
		try {
			socket = IO.socket("http://terracraft.us:8080");
			socket.connect();
		} catch (Exception e) {
			Gdx.app.log("SocketIO","Everything broke, " + e);
		}
	}

	public void configSocketEvents() {
		socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				Gdx.app.log("SocketIO", "Connected");
				player = new Starship(playerShip);
			}
		}).on("socketID", new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				JSONObject data = (JSONObject) args[0];
				try {
					String id = data.getString("id");
					Gdx.app.log("SocketIO","My ID: " + id);
				} catch (JSONException e) {
					Gdx.app.log("SocketIO","Error getting ID");
				}
			}
		}).on("newPlayer", new Emitter.Listener() {
			@Override
			public void call(Object... args) {
				JSONObject data = (JSONObject) args[0];
				try {
					String playerId = data.getString("id");
					Gdx.app.log("SocketIO","New player connected with ID: " + playerId);
					friendlyPlayers.put(playerId, new Starship(friendlyShip));
				} catch (JSONException e) {
					Gdx.app.log("SocketIO","Error getting new player ID");
				}
			}
		}).on("playerDisconnected", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                try {
                    String id = data.getString("id");
                    friendlyPlayers.remove(id);
                } catch (JSONException e) {
                    Gdx.app.log("SocketIO","Error getting new player ID");
                }
            }
		}).on("getPlayers", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONArray objects = (JSONArray) args[0];
                try {
                    for (int i = 0; i < objects.length(); i++) {
                        Starship coopPlayer = new Starship(friendlyShip);
                        Vector2 position = new Vector2();
                        position.x = ((Double) objects.getJSONObject(i).getDouble("x")).floatValue();
                        position.y = ((Double) objects.getJSONObject(i).getDouble("y")).floatValue();
                        coopPlayer.setPosition(position.x, position.y);

                        friendlyPlayers.put(objects.getJSONObject(i).getString("id"), coopPlayer);
                    }
                } catch (JSONException e) {

                }
            }
        }).on("playerMoved", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                try {
                    String playerId = data.getString("id");
                    Double x = data.getDouble("x");
                    Double y = data.getDouble("y");

                    if (friendlyPlayers.get(playerId) != null) {
                        friendlyPlayers.get(playerId).setPosition(x.floatValue(), y.floatValue());
                    }

                } catch (JSONException e) {
                    Gdx.app.log("SocketIO", "Error getting movement");
                }
            }
        });
	}
}
