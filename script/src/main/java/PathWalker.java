import com.aposbot.Constants;

import javax.imageio.ImageIO;
import java.awt.List;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class PathWalker extends Script {

	/*
	 * - Features, etc.
	 * Calculates a path from one point in the world to another and walks
	 * there.
	 * Prefers road over ground.
	 * Can open many doors. Extra IDs and specifics like which key needs to
	 * be used are appreciated.
	 *
	 * - Limitations, etc.
	 * Can't change levels (with ladders, etc) or use any kind of
	 * teleportation point.
	 * No proper handling of direction with bounds.
	 * Object information in the default loaded map may be inaccurate.
	 *
	 * - Credits
	 * Stormy
	 * Wikipedia
	 * Xueqiao Xu <xueqiaoxu@gmail.com>
	 *
	 * Contributions are appreciated.
	 */

	public static final Location[] LOCATIONS = new Location[]{
		new Location("Al Kharid", 87, 695, true),
		new Location("AK Mine Crossroads", 76, 573, false),
		new Location("Ardougne North", 580, 573, true),
		new Location("Ardougne South", 550, 612, true),
		new Location("Catherby", 440, 496, true),
		new Location("Draynor", 220, 635, true),
		new Location("Dwarf Mine/cannon", 280, 490, false),
		new Location("Edgeville", 215, 450, true),
		new Location("Khazard House", 615, 683, false),
		new Location("Falador East", 285, 570, true),
		new Location("Falador West", 330, 555, true),
		new Location("Gnome Tree", 692, 494, false),
		new Location("Goblin Village", 326, 453, false),
		new Location("Ice Cave Ladder", 288, 711, false),
		new Location("Lumbridge", 128, 640, false),
		new Location("Port Sarim", 270, 625, false),
		new Location("Rimmington", 320, 653, false),
		new Location("Seers Village", 500, 453, true),
		new Location("Shilo Village", 401, 849, true),
		new Location("Varrock East", 102, 511, true),
		new Location("Varrock West", 150, 505, true),
		new Location("Yanille", 587, 752, true),
		new Location("Bone Yard", 700, 648, false),
		new Location("Legends Guild", 512, 554, false),
		new Location("Heroes Guild", 372, 443, false),
		new Location("Fishing Guild", 586, 527, false),
		new Location("Crafting Guild", 347, 599, false),
		new Location("Shantay pass", 62, 730, false),
		new Location("Lost City Hut", 128, 686, false)
	};
	private static final boolean DEBUG = false;
	private static final int WORLD_W = 900;
	private static final int WORLD_H = 4050;
	private static final int[] objects_1 = new int[]{
		64, 60, 137, 138, 93
	};
	private static final int[] bounds_1 = new int[]{
		2, 8, 55, 68, 44, 74, 117
	};
	private final Extension client;
	private Node[][] nodes;
	private Node[] path;
	private long wait_time;
	private int path_ptr;
	private Frame frame;
	private List choice;
	private TextField textField;
	private long start_time;

	public PathWalker(final Extension client) {
		super(client);
		this.client = client;
	}

	public static void main(final String[] argv) {
		final PathWalker pw = new PathWalker(null);
		pw.init("");
		while (pw.frame.isVisible()) {
			try {
				Thread.sleep(50L);
			} catch (final InterruptedException ignored) {
			}
		}
		System.exit(0);
	}

	@Override
	public void init(final String params) {
		start_time = -1L;

		if (nodes == null) {
			final File file = Constants.PATH_MAP.resolve("data.gz").toFile();

			System.out.printf("[%s] Reading map... %n", this);

			final byte[][] walkable = new byte[WORLD_W][WORLD_H];
			try (final GZIPInputStream in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(file)))) {
				for (int i = 0; i < WORLD_W; ++i) {
					int read = 0;
					do {
						final int r = in.read(walkable[i], read, WORLD_H - read);
						if (r == -1) {
							throw new IOException("Unexpected EOF");
						}
						read += r;
					} while (read != WORLD_H);
				}
			} catch (final IOException ex) {
				System.err.printf("[%s] Failed to read map: %s%n", this, ex);
				return;
			}

			nodes = new Node[WORLD_W][WORLD_H];

			for (int x = 0; x < WORLD_W; ++x) {
				for (int y = 0; y < WORLD_H; ++y) {
					final byte i = walkable[x][y];
					if (i != 0) {
						final Node n = new Node(x, y);
						n.walkable = i;
						nodes[x][y] = n;
					}
				}
			}

			System.gc();
			System.out.printf("[%s] Nodes initialized.%n", this);
		}

		// The bot will always call init with "".
		// If init is called with null, it is being called by another script,
		// so don't create the UI.
		if (params != null) {
			createFrame();
		}
	}

	@Override
	public int main() {
		if (start_time == -1L) {
			start_time = System.currentTimeMillis();
		}
		if (inCombat()) {
			resetWait();
			walkTo(getX(), getY());
			return random(400, 600);
		}
		if (!walkPath()) {
			System.out.printf("[%s] Destination reached.%n", this);
			client.stopScript();
			Toolkit.getDefaultToolkit().beep();
		}
		return 0;
	}

	public void resetWait() {
		wait_time = System.currentTimeMillis();
	}

	public boolean walkPath() {
		if (path == null) return false;
		final Node last = path[path.length - 1];
		if (getX() == last.x && getY() == last.y) {
			path = null;
			return false;
		}
		long c_time = System.currentTimeMillis();
		if (c_time >= wait_time) {
			final Node n = getCurrentDest();
			if (n == null) return true;
			final int x = n.x;
			final int y = n.y;
			if (isAtApproxCoords(331, 487, 10) && (n.x > 341)) {
				atObject(341, 487);
				wait_time = c_time + 8000;

			} else if (isAtApproxCoords(352, 487, 10) && (n.x <= 341)) {
				atObject(341, 487);
				wait_time = c_time + 8000;
			} else if (isAtApproxCoords(343, 591, 10) && (n.y < 581)) {
				atObject(343, 581);
				wait_time = c_time + 8000;
			} else if (isAtApproxCoords(343, 570, 10) && (n.y >= 581)) {
				atObject(343, 581);
				wait_time = c_time + 8000;
			} else if (isAtApproxCoords(703, 542, 10) && (n.y <= 531)) {
				atObject(703, 531);
				wait_time = c_time + 8000;
			} else if (isAtApproxCoords(703, 521, 10) && (n.y > 531)) {
				atObject(703, 531);
				wait_time = c_time + 8000;
			} else if (isAtApproxCoords(445, 682, 10) && (n.x < 435)) {
				atObject(434, 682);
				wait_time = c_time + 8000;
			} else if (isAtApproxCoords(424, 521, 10) && (n.x >= 435)) {
				atObject(434, 682);
				wait_time = c_time + 8000;
			} else if (isAtApproxCoords(111, 152, 10) && (n.y < 142)) {
				atObject(111, 142);
				c_time = wait_time;
			} else if (isAtApproxCoords(117, 131, 10) && (n.y >= 142)) {
				atObject(111, 142);
				c_time = wait_time;
			} else {
				walkTo(x, y);
			}
			final int d = distanceTo(x, y);
			if (d != 0) {
				wait_time = c_time + random(500 * d, 600 * d);
			} else {
				wait_time = c_time + random(600, 800);
			}
		}
		return true;
	}

	private Node getCurrentDest() {
		final long c_time = System.currentTimeMillis();
		int ptr = path_ptr;
		int x, y;
		final int orig = ptr;
		do {
			if (ptr >= (path.length - 1)) {
				break;
			}
			final Node cur = path[ptr];
			x = cur.x;
			y = cur.y;
			final Node next = path[++ptr];
			if (!isReachable(next.x, next.y) && handleObstacles(x, y)) {
				// you may wish to modify this.
				wait_time = c_time + random(2500, 3000);
				path_ptr = ptr - 1;
				return null;
			}
		} while (distanceTo(x, y) < 6);

		ptr = orig;

		int min_dist = random(7, 18);
		final int max_dist = 20;
		int dist;
		int loop = 0;
		do {
			if ((++ptr) >= path.length) {
				min_dist = random(1, 18);
				ptr = orig;
			}
			final Node n = path[ptr];
			x = n.x;
			y = n.y;
			dist = distanceTo(x, y);
			if (dist > max_dist) {
				min_dist = random(1, 18);
				ptr = orig;
			}
			if ((loop++) > 500) {
				System.err.printf("[%s] Pathing failure.%n", this);
				return null;
			}
		} while (dist < min_dist || !isReachable(x, y));
		path_ptr = ptr;
		return path[path_ptr];
	}

	private boolean handleObstacles(final int x, final int y) {
		final int id = getWallObjectIdFromCoords(x, y);
		if (id != -1) {
			for (final int i : bounds_1) {
				if (id != i) continue;
				atWallObject(x, y);
				return true;
			}
		}
		// is this ridiculous or not? heh
		if (handleObject(x, y)) return true;
		if (handleObject(x + 1, y)) return true;
		if (handleObject(x - 1, y)) return true;
		if (handleObject(x, y + 1)) return true;
		if (handleObject(x, y - 1)) return true;
		if (handleObject(x - 1, y - 1)) return true;
		if (handleObject(x + 1, y + 1)) return true;
		if (handleObject(x - 1, y + 1)) return true;
		return handleObject(x + 1, y - 1);
	}

	private boolean handleObject(final int x, final int y) {
		final int id = getObjectIdFromCoords(x, y);
		if (id == -1) return false;
		for (final int i : objects_1) {
			if (id != i) continue;
			atObject(x, y);
			return true;
		}
		return false;
	}

	@Override
	public void paint() {
		final int x = 320;
		int y = 46;
		drawString("Storm's Path Walker", x - 1, y, 4, 0x1E90FF);
		y += 15;
		drawString("Gate Support by kRiStOf", x - 1, y, 4, 0x1E90FF);
		y += 15;
		drawString("Runtime: " + get_time_since(start_time), x, y, 1, 0xFFFFFF);
		drawVLine(x - 7, 36, y - 32, 0x1E90FF);
		drawHLine(x - 7, y + 3, 196, 0x1E90FF);
	}

	private static String get_time_since(final long t) {
		final long millis = (System.currentTimeMillis() - t) / 1000;
		final long second = millis % 60;
		final long minute = (millis / 60) % 60;
		final long hour = (millis / (60 * 60)) % 24;
		final long day = (millis / (60 * 60 * 24));

		if (day > 0L) {
			return String.format("%02d days, %02d hrs, %02d mins",
				day, hour, minute);
		}
		if (hour > 0L) {
			return String.format("%02d hours, %02d mins, %02d secs",
				hour, minute, second);
		}
		if (minute > 0L) {
			return String.format("%02d minutes, %02d seconds",
				minute, second);
		}
		return String.format("%02d seconds", second);
	}

	private void createFrame() {
		if (frame == null) {
			final Button okButton = new Button("OK");
			okButton.addActionListener(e -> {
				final String[] split = textField.getText().split(",");
				final int x = Integer.parseInt(split[0]);
				final int y = Integer.parseInt(split[1]);

				final Path path = calcPath(getX(), getY(), x, y, 5);

				if (path == null) {
					System.err.printf("[%s] Failed to calculate path from: (%d,%d)%n", this, getX(), getY());
					return;
				}

				if (DEBUG) {
					final Node start = this.path[0];
					final Node end = this.path[this.path.length - 1];
					generatePathImage(start.x, start.y, end.x, end.y);
				}

				setPath(path);
				System.gc();

				System.out.printf("[%s] Ready to run.%n", this);
				client.displayMessage("@cya@Pathwalker: Ready to run.");

				frame.setVisible(false);
			});

			final Button cancelButton = new Button("Cancel");
			cancelButton.addActionListener(e -> exit());

			final Panel buttonPanel = new Panel();
			buttonPanel.add(okButton);
			buttonPanel.add(cancelButton);

			final Panel textPanel = new Panel();
			textPanel.setLayout(new GridLayout(0, 2, 2, 2));
			textPanel.add(new Label("Target location"));
			textPanel.add(textField = new TextField("0,0"));

			choice = new List(LOCATIONS.length / 2);

			for (final Location l : LOCATIONS) {
				choice.add(l.name);
			}

			choice.addItemListener(e -> {
				final Location location = LOCATIONS[choice.getSelectedIndex()];
				textField.setText(String.format("%s,%s", location.x, location.y));
			});

			final Panel choicePanel = new Panel();
			choicePanel.setLayout(new BorderLayout());
			choicePanel.add(new Label("Preset targets", Label.CENTER),
				BorderLayout.NORTH);
			choicePanel.add(choice, BorderLayout.CENTER);

			frame = new Frame(getClass().getSimpleName());
			frame.addWindowListener(
				new WindowAdapter() {
					@Override
					public void windowClosing(final WindowEvent e) {
						exit();
					}
				}
			);
			frame.setIconImages(Constants.ICONS);
			frame.add(textPanel, BorderLayout.NORTH);
			frame.add(choicePanel, BorderLayout.CENTER);
			frame.add(buttonPanel, BorderLayout.SOUTH);
			frame.pack();
			frame.setMinimumSize(frame.getSize());
			frame.setSize(245, 280);
		}

		frame.toFront();
		frame.setLocationRelativeTo(null);
		frame.requestFocus();
		frame.setVisible(true);
	}

	public Path calcPath(int x1, int y1, final int x2, final int y2, final int radius) {
		Path path = null;

		// https://stackoverflow.com/a/398302
		int dx = 0;
		int _dx;
		int dy = -1;

		int i = 0;

		do {
			if (isReachable(x1, y1) && !isObjectAt(x1, y1) && (path = calcPath(x1, y1, x2, y2)) != null) {
				break;
			}

			if ((x1 == y1) || (x1 < 0 && x1 == -y1) || (x1 > 0 && x1 == 1 - y1)) {
				_dx = dx;
				dx = -dy;
				dy = _dx;
			}

			x1 += dx;
			y1 += dy;

			i++;
		} while (i < Math.pow(radius * 2 + 1, 2));

		return path;
	}

	private void generatePathImage(final int x1, final int y1, final int x2, final int y2) {
		final BufferedImage image = getMapImage();
		if (image == null) {
			return;
		}

		System.out.printf("[%s] Generating path image... %n", this);
		final Graphics g = image.getGraphics();
		g.setColor(Color.GREEN);
		final int len = path.length;
		for (final Node p : path) {
			g.fillOval(WORLD_W - 1 - p.x, p.y, 3, 3);
		}
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		g.setColor(Color.BLACK);
		g.drawString("Start", WORLD_W - x1, y1 + 1);
		g.drawString("Goal", WORLD_W - x2, y2 + 1);
		g.setColor(Color.WHITE);
		g.drawString("Start", WORLD_W - 1 - x1, y1);
		g.drawString("Goal", WORLD_W - 1 - x2, y2);
		System.out.printf("[%s] Path image generated.%n", this);
		System.out.printf("[%s] Writing path image... %n", this);
		try {
			final File file = Constants.PATH_MAP.resolve("path.png").toFile();
			ImageIO.write(image, "PNG", file);
			System.out.printf("[%s] Path image written.%n", this);
		} catch (final Throwable t) {
			System.err.printf("[%s] Failed to write path image: %s%n", this, t);
		}
	}

	public void setPath(final Path p) {
		if (p == null) {
			path = null;
			return;
		}
		if (p.n == path) return;
		path = p.n;
		wait_time = 0;
		path_ptr = 0;
	}

	private void exit() {
		nodes = null;
		client.stopScript();
		frame.setVisible(false);
		System.err.printf("[%s] Cancelled.%n", this);
		client.displayMessage("@cya@Pathwalker: Cancelled.");
	}

	public Path calcPath(final int x1, final int y1, final int x2, final int y2) {
		final Node start = getNode(nodes, x1, y1);
		if (start == null) return null;
		final Node end = getNode(nodes, x2, y2);
		if (end == null) return null;
		final Node[] n = astar(start, end);
		if (n == null) return null;
		final Path p = new Path();
		p.n = n;
		return p;
	}

	private BufferedImage getMapImage() {
		final File file = Constants.PATH_MAP.resolve("map.png").toFile();

		System.out.printf("[%s] Reading map image... %n", this);
		try {
			final BufferedImage image = ImageIO.read(file);
			System.out.printf("[%s] Map image read.%n", this);
			return image;
		} catch (final IOException ex) {
			System.err.printf("[%s] Failed to read map image: %s%n", this, ex);
		}
		return null;
	}

	private static Node getNode(final Node[][] nodes, final int x, final int y) {
		if (x < 0 || x > (WORLD_W - 1)) {
			return null;
		}
		if (y < 0 || y > (WORLD_H - 1)) {
			return null;
		}
		return nodes[x][y];
	}

	private Node[] astar(final Node start, final Node goal) {
		if (DEBUG) {
			System.out.print("Calculating path from " + start + " to " + goal + "... ");
		}

		final long start_ms = System.currentTimeMillis();

		start.f = (short) start.estHeuristicCost(goal);

		final Deque<Node> open = new ArrayDeque<>(32);
		open.add(start);
		start.open = true;

		// The map of navigated nodes
		final Map<Node, Node> came_from = new HashMap<>();

		final Node[][] nodes = this.nodes;

		while (!open.isEmpty()) {
			final Node cur = getLowestFScore(open);
			if (cur.equals(goal)) {
				final Node[] n = constructPath(came_from, start, goal);
				resetNodes(nodes);
				if (DEBUG) {
					System.out.print("done. ms taken: ");
					System.out.println(System.currentTimeMillis() - start_ms);
				}
				return n;
			}
			open.remove(cur);
			cur.open = false;
			cur.closed = true;
			for (final Node n : cur.getNeighbors(nodes)) {
				final int t_gscore = cur.g + n.distFrom(cur);
				final int t_fscore = t_gscore + n.estHeuristicCost(goal);
				if (n.closed && t_fscore >= n.f) {
					continue;
				}
				if (!n.open) {
					came_from.put(n, cur);
					n.g = (short) t_gscore;
					n.f = (short) t_fscore;
					open.add(n);
					n.open = true;
				}
			}
		}

		resetNodes(nodes);
		if (DEBUG) {
			System.out.print("failed! ms taken: ");
			System.out.println(System.currentTimeMillis() - start_ms);
		}
		return null;
	}

	private static Node getLowestFScore(final Deque<Node> open) {
		Node best_n = null;
		int best_f = Integer.MAX_VALUE;
		int f;
		for (final Node n : open) {
			f = n.f;
			if (f < best_f) {
				best_n = n;
				best_f = f;
			}
		}
		return best_n;
	}

	private static Node[] constructPath(
		final Map<Node, Node> came_from, final Node start, final Node goal) {

		final Deque<Node> path = new ArrayDeque<>();
		Node p = came_from.get(goal);
		while (p != start) {
			path.push(p);
			p = came_from.get(p);
		}
		path.push(p);
		path.add(goal);
		return path.toArray(new Node[0]);
	}

	private static void resetNodes(final Node[][] nodes) {
		for (int x = 0; x < WORLD_W; ++x) {
			for (int y = 0; y < WORLD_H; ++y) {
				final Node n = nodes[x][y];
				if (n == null) continue;
				n.reset();
			}
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	public final Path calcPath(final int x, final int y) {
		return calcPath(getX(), getY(), x, y);
	}

	public final Location getNearestBank(final int x, final int y) {
		final ArrayList<Location> ordered = new ArrayList<>();
		for (final Location loc : LOCATIONS) {
			if (loc.bank) {
				ordered.add(loc);
			}
		}
		ordered.sort((l1, l2) -> {
			final int dist1 = distanceTo(x, y, l1.x, l1.y);
			final int dist2 = distanceTo(x, y, l2.x, l2.y);
			return Integer.compare(dist1, dist2);
		});
		int best_dist = Integer.MAX_VALUE;
		Location best_loc = null;
		for (final Location loc : ordered) {
			if (distanceTo(x, y, loc.x, loc.y) > best_dist) {
				continue;
			}
			final int dist = pathLength(x, y, loc.x, loc.y);
			if (dist < best_dist) {
				best_dist = dist;
				best_loc = loc;
			}
		}
		return best_loc;
	}

	public int pathLength(final int x1, final int y1, final int x2, final int y2) {
		final Node start = getNode(nodes, x1, y1);
		if (start == null) return Integer.MAX_VALUE;
		final Node end = getNode(nodes, x2, y2);
		if (end == null) return Integer.MAX_VALUE;
		final Node[] n = astar(start, end);
		if (n == null) return Integer.MAX_VALUE;
		return n.length;
	}

	/**
	 * kRiStOf's edits:
	 * /* added paint
	 * /* added runtime to paint
	 */

	// class for encapsulation
	public static class Path {
		private Node[] n;
	}

	private static final class Node {
		final short x;
		final short y;
		// Cost from start along best known path.
		short g;
		// Estimated total cost from start to goal through y.
		short f;
		// Heuristic cost to goal
		short h;
		byte walkable;
		ArrayList<Node> neighbors;
		// Already evaluated.
		boolean closed;
		// Tentative node to be evaluated
		boolean open;

		Node(final int x, final int y) {
			this.x = (short) x;
			this.y = (short) y;
			h = -1;
		}

		void reset() {
			h = -1;
			g = 0;
			f = 0;
			closed = false;
			open = false;
		}

		int distFrom(final Node n) {
			final int sx = x - n.x;
			if (sx == 0) return 1;
			final int sy = y - n.y;
			if (sy == 0) return 1;
			int dx = Math.abs(sx);
			int dy = Math.abs(sy);
			dx *= dx;
			dy *= dy;
			return (int) Math.sqrt(dx + dy);
		}

		int estHeuristicCost(final Node n) {
			// manhattan
			if (h == -1) {
				h = (short) ((2 - walkable) *
					(Math.abs(x - n.x) +
						Math.abs(y - n.y)));
			}
			return h;
		}

		ArrayList<Node> getNeighbors(final Node[][] nodes) {
			if (neighbors != null) {
				return neighbors;
			}

			final boolean allowDiagonal = true;
			final boolean dontCrossCorners = true;

			boolean s0 = false;
			boolean s1 = false;
			boolean s2 = false;
			boolean s3 = false;

			final boolean d0;
			final boolean d1;
			final boolean d2;
			final boolean d3;

			final int x = this.x;
			final int y = this.y;
			Node n;
			final ArrayList<Node> neighbors = new ArrayList<>(0);

			n = getNode(nodes, x, y - 1);
			if (n != null) {
				neighbors.add(n);
				s0 = true;
			}
			n = getNode(nodes, x + 1, y);
			if (n != null) {
				neighbors.add(n);
				s1 = true;
			}
			n = getNode(nodes, x, y + 1);
			if (n != null) {
				neighbors.add(n);
				s2 = true;
			}
			n = getNode(nodes, x - 1, y);
			if (n != null) {
				neighbors.add(n);
				s3 = true;
			}

			if (!allowDiagonal) {
				return neighbors;
			}

			if (dontCrossCorners) {
				d0 = s3 && s0;
				d1 = s0 && s1;
				d2 = s1 && s2;
				d3 = s2 && s3;
			} else {
				d0 = s3 || s0;
				d1 = s0 || s1;
				d2 = s1 || s2;
				d3 = s2 || s3;
			}

			n = getNode(nodes, x - 1, y - 1);
			if (n != null && d0) {
				neighbors.add(n);
			}
			n = getNode(nodes, x + 1, y - 1);
			if (n != null && d1) {
				neighbors.add(n);
			}
			n = getNode(nodes, x + 1, y + 1);
			if (n != null && d2) {
				neighbors.add(n);
			}
			n = getNode(nodes, x - 1, y + 1);
			if (n != null && d3) {
				neighbors.add(n);
			}

			this.neighbors = neighbors;
			return neighbors;
		}

		@Override
		public String toString() {
			return x + "," + y;
		}
	}

	public static class Location {

		public String name;
		public int x;
		public int y;
		public boolean bank;

		public Location(final String name, final int x, final int y, final boolean b) {
			this.name = name;
			this.x = x;
			this.y = y;
			bank = b;
		}

		@Override
		public String toString() {
			final String s = bank ? "a bank" : "not a bank";
			return String.format("%s (%d, %d), %s", name, x, y, s);
		}
	}
}