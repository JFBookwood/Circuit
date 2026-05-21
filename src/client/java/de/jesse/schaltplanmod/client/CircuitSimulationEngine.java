package de.jesse.schaltplanmod.client;

import de.jesse.schaltplanmod.circuit.CircuitComponentType;
import de.jesse.schaltplanmod.circuit.PortRole;
import de.jesse.schaltplanmod.circuit.SchematicPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class CircuitSimulationEngine {
	private CircuitSimulationEngine() {
	}

	public static SimulationSnapshot simulate(List<PlacedComponent> components, Map<Integer, Boolean> manualInputs, int maxTicks) {
		List<Node> nodes = collectNodes(components);
		List<WireNet> nets = collectWireNets(components);
		attachPorts(nodes, nets);

		Map<Integer, Boolean> outputs = new LinkedHashMap<>();
		Map<Integer, Boolean> previousOutputs = new LinkedHashMap<>();
		List<Map<Integer, Integer>> timeline = new ArrayList<>();
		List<Map<Integer, Integer>> netTimeline = new ArrayList<>();
		Set<String> seenStates = new HashSet<>();
		boolean loopDetected = false;
		boolean stabilized = false;
		int ticks = 0;

		for (; ticks < maxTicks; ticks++) {
			Map<WireNet, Integer> netSignal = new LinkedHashMap<>();
			for (WireNet net : nets) {
				netSignal.put(net, 0);
			}

			for (Node node : nodes) {
				int output = signalFor(node, outputs, manualInputs, netSignal);
				if (output > 0) {
					for (WireNet net : node.outputNets) {
						netSignal.put(net, Math.max(netSignal.getOrDefault(net, 0), output));
					}
				}
			}

			Map<Integer, Boolean> nextOutputs = new LinkedHashMap<>();
			for (Node node : nodes) {
				nextOutputs.put(node.id, evaluate(node, manualInputs, netSignal, outputs));
			}

			timeline.add(snapshotSignals(nodes, nextOutputs, manualInputs, netSignal));
			netTimeline.add(snapshotNetSignals(netSignal));
			String stateKey = stateKey(nextOutputs, netSignal);
			if (nextOutputs.equals(previousOutputs)) {
				stabilized = true;
				outputs = nextOutputs;
				break;
			}
			if (!seenStates.add(stateKey)) {
				loopDetected = true;
				outputs = nextOutputs;
				break;
			}

			previousOutputs = outputs;
			outputs = nextOutputs;
		}

		return new SimulationSnapshot(nodes, nets, outputs, timeline, netTimeline, ticks + 1, stabilized, loopDetected);
	}

	private static List<Node> collectNodes(List<PlacedComponent> components) {
		List<Node> nodes = new ArrayList<>();
		for (int index = 0; index < components.size(); index++) {
			PlacedComponent component = components.get(index);
			if (!isWireNetPart(component.schematic().type())) {
				nodes.add(new Node(index, component));
			}
		}
		return nodes;
	}

	private static List<WireNet> collectWireNets(List<PlacedComponent> components) {
		List<WireNet> nets = new ArrayList<>();
		Map<GridPoint, WireCell> cells = new LinkedHashMap<>();

		for (PlacedComponent component : components) {
			if (!isWireNetPart(component.schematic().type())) {
				continue;
			}
			GridPoint point = new GridPoint(component.gridX(), component.gridZ());
			cells.computeIfAbsent(point, ignored -> new WireCell()).groupIds.add(component.groupId());
		}

		Set<GridPoint> visited = new HashSet<>();
		for (GridPoint start : cells.keySet()) {
			if (!visited.add(start)) {
				continue;
			}

			WireNet net = new WireNet(nets.size());
			ArrayList<GridPoint> queue = new ArrayList<>();
			queue.add(start);
			for (int cursor = 0; cursor < queue.size(); cursor++) {
				GridPoint point = queue.get(cursor);
				net.points.add(point);
				for (long groupId : cells.get(point).groupIds) {
					if (groupId > 0L) {
						net.groupIds.add(groupId);
					}
				}

				for (GridPoint neighbor : point.orthogonalNeighbors()) {
					if (cells.containsKey(neighbor) && visited.add(neighbor)) {
						queue.add(neighbor);
					}
				}
			}
			nets.add(net);
		}

		if (nets.isEmpty()) {
			WireNet implicit = new WireNet(0);
			nets.add(implicit);
		}
		return nets;
	}

	private static void attachPorts(List<Node> nodes, List<WireNet> nets) {
		for (Node node : nodes) {
			if (node.component.schematic().type() == CircuitComponentType.REPEATER_DELAY) {
				attachRepeaterPorts(node, nets);
				continue;
			}

			for (SchematicPort port : node.component.schematic().ports()) {
				GridPoint portPoint = rotatedPortPoint(node.component, port);
				WireNet net = nearestNet(portPoint, nets);
				net.attachedPorts++;
				if (port.role() == PortRole.INPUT) {
					node.inputNets.add(net);
				} else {
					node.outputNets.add(net);
				}
			}
			attachFallbackPorts(node, nets);
		}
	}

	private static void attachRepeaterPorts(Node node, List<WireNet> nets) {
		GridPoint center = new GridPoint(node.component.gridX(), node.component.gridZ());
		Direction outputDirection = Direction.fromRotation(node.component.rotation());
		GridPoint inputPoint = outputDirection.opposite().offset(center);
		GridPoint outputPoint = outputDirection.offset(center);
		WireNet inputNet = netAt(inputPoint, nets);
		WireNet outputNet = netAt(outputPoint, nets);

		if (inputNet != null) {
			node.inputNets.add(inputNet);
			inputNet.attachedPorts++;
		}
		if (outputNet != null) {
			node.outputNets.add(outputNet);
			outputNet.attachedPorts++;
		}
		if (node.inputNets.isEmpty()) {
			WireNet nearest = nearestNet(inputPoint, nets);
			node.inputNets.add(nearest);
			nearest.attachedPorts++;
		}
		if (node.outputNets.isEmpty()) {
			WireNet nearest = nearestNet(outputPoint, nets);
			node.outputNets.add(nearest);
			nearest.attachedPorts++;
		}
	}

	private static void attachFallbackPorts(Node node, List<WireNet> nets) {
		CircuitComponentType type = node.component.schematic().type();
		GridPoint anchor = componentAnchorPoint(node.component);
		WireNet nearest = nearestNet(anchor, nets);

		if (node.outputNets.isEmpty() && producesSignal(type)) {
			for (WireNet net : nearbyNets(anchor, nets, sourceFanoutDistance(type))) {
				node.outputNets.add(net);
				net.attachedPorts++;
			}
			if (node.outputNets.isEmpty()) {
				node.outputNets.add(nearest);
				nearest.attachedPorts++;
			}
		}
		if (node.inputNets.isEmpty() && consumesSignal(type)) {
			node.inputNets.add(nearest);
			nearest.attachedPorts++;
		}
	}

	private static boolean producesSignal(CircuitComponentType type) {
		return switch (type) {
			case SWITCH, BUTTON, VCC, CLOCK, OBSERVER_CLOCK, ONE_TICK_PULSER, NOT, AND, NAND, OR, XOR, LATCH, D_FLIPFLOP, T_FLIPFLOP_COPPER_BULB -> true;
			default -> false;
		};
	}

	private static boolean consumesSignal(CircuitComponentType type) {
		return switch (type) {
			case LAMP, NOT, AND, NAND, OR, XOR, LATCH, D_FLIPFLOP, T_FLIPFLOP_COPPER_BULB -> true;
			default -> false;
		};
	}

	private static int sourceFanoutDistance(CircuitComponentType type) {
		return switch (type) {
			case SWITCH, BUTTON, VCC -> 3;
			default -> 1;
		};
	}

	private static List<WireNet> nearbyNets(GridPoint point, List<WireNet> nets, int maxDistance) {
		return nets.stream()
				.filter(net -> net.points.stream()
						.mapToInt(wire -> Math.abs(wire.x() - point.x()) + Math.abs(wire.z() - point.z()))
						.min()
						.orElse(9999) <= maxDistance)
				.toList();
	}

	private static GridPoint componentAnchorPoint(PlacedComponent component) {
		return new GridPoint(
				component.gridX() + Math.max(0, component.schematic().size().x() / 2),
				component.gridZ() + Math.max(0, component.schematic().size().z() / 2)
		);
	}

	private static WireNet nearestNet(GridPoint point, List<WireNet> nets) {
		return nets.stream()
				.min(Comparator.comparingInt(net -> net.points.stream()
						.mapToInt(wire -> Math.abs(wire.x() - point.x()) + Math.abs(wire.z() - point.z()))
						.min()
						.orElse(9999)))
				.orElse(nets.getFirst());
	}

	private static WireNet netAt(GridPoint point, List<WireNet> nets) {
		for (WireNet net : nets) {
			if (net.points.contains(point)) {
				return net;
			}
		}
		return null;
	}

	private static GridPoint rotatedPortPoint(PlacedComponent component, SchematicPort port) {
		GridPoint rotated = rotatePoint(
				port.connectionPosition().x(),
				port.connectionPosition().z(),
				component.schematic().size().x(),
				component.schematic().size().z(),
				component.rotation()
		);
		return new GridPoint(component.gridX() + rotated.x(), component.gridZ() + rotated.z());
	}

	private static int signalFor(Node node, Map<Integer, Boolean> outputs, Map<Integer, Boolean> manualInputs, Map<WireNet, Integer> netSignal) {
		return switch (node.component.schematic().type()) {
			case SWITCH, BUTTON -> manualInputs.getOrDefault(node.id, false) ? 15 : 0;
			case VCC -> 15;
			case REPEATER_DELAY -> outputs.getOrDefault(node.id, false) ? 15 : 0;
			default -> outputs.getOrDefault(node.id, false) ? 15 : 0;
		};
	}

	private static boolean evaluate(Node node, Map<Integer, Boolean> manualInputs, Map<WireNet, Integer> netSignal, Map<Integer, Boolean> previousOutputs) {
		CircuitComponentType type = node.component.schematic().type();
		List<Boolean> inputs = node.inputNets.stream().map(net -> netSignal.getOrDefault(net, 0) > 0).toList();
		return switch (type) {
			case SWITCH, BUTTON -> manualInputs.getOrDefault(node.id, false);
			case VCC -> true;
			case LAMP -> inputs.stream().anyMatch(Boolean::booleanValue);
			case NOT -> inputs.stream().noneMatch(Boolean::booleanValue);
			case AND -> inputs.size() >= 2 && inputs.stream().allMatch(Boolean::booleanValue);
			case NAND -> !(inputs.size() >= 2 && inputs.stream().allMatch(Boolean::booleanValue));
			case OR -> inputs.stream().anyMatch(Boolean::booleanValue);
			case XOR -> inputs.stream().filter(Boolean::booleanValue).count() % 2 == 1;
			case REPEATER_DELAY -> inputs.stream().anyMatch(Boolean::booleanValue);
			case LATCH, D_FLIPFLOP, T_FLIPFLOP_COPPER_BULB -> previousOutputs.getOrDefault(node.id, false);
			default -> inputs.stream().anyMatch(Boolean::booleanValue);
		};
	}

	private static Map<Integer, Integer> snapshotSignals(List<Node> nodes, Map<Integer, Boolean> outputs, Map<Integer, Boolean> manualInputs, Map<WireNet, Integer> netSignal) {
		Map<Integer, Integer> signals = new LinkedHashMap<>();
		for (Node node : nodes) {
			signals.put(node.id, signalFor(node, outputs, manualInputs, netSignal));
		}
		return signals;
	}

	private static Map<Integer, Integer> snapshotNetSignals(Map<WireNet, Integer> netSignal) {
		Map<Integer, Integer> signals = new LinkedHashMap<>();
		for (Map.Entry<WireNet, Integer> entry : netSignal.entrySet()) {
			signals.put(entry.getKey().id, entry.getValue());
		}
		return signals;
	}

	private static String stateKey(Map<Integer, Boolean> outputs, Map<WireNet, Integer> netSignal) {
		return outputs + "|" + netSignal.entrySet().stream()
				.map(entry -> entry.getKey().id + "=" + entry.getValue())
				.collect(Collectors.joining(","));
	}

	private static boolean isWireNetPart(CircuitComponentType type) {
		return type == CircuitComponentType.WIRE || type == CircuitComponentType.OBSERVER_WIRE;
	}

	private static GridPoint rotatePoint(int x, int z, int sizeX, int sizeZ, int rotation) {
		return switch (rotation) {
			case 1 -> new GridPoint(sizeZ - 1 - z, x);
			case 2 -> new GridPoint(sizeX - 1 - x, sizeZ - 1 - z);
			case 3 -> new GridPoint(z, sizeX - 1 - x);
			default -> new GridPoint(x, z);
		};
	}

	public record SimulationSnapshot(List<Node> nodes, List<WireNet> nets, Map<Integer, Boolean> outputs,
									 List<Map<Integer, Integer>> timeline, List<Map<Integer, Integer>> netTimeline,
									 int ticks, boolean stabilized, boolean loopDetected) {
		public List<Node> manualInputs() {
			return nodes.stream()
					.filter(node -> node.component.schematic().type() == CircuitComponentType.SWITCH
							|| node.component.schematic().type() == CircuitComponentType.BUTTON)
					.toList();
		}

		public List<Node> visibleOutputs() {
			return nodes.stream()
					.filter(node -> node.component.schematic().type() == CircuitComponentType.LAMP
							|| !node.outputNets.isEmpty())
					.toList();
		}

		public int signalForWireGroup(long groupId) {
			if (netTimeline.isEmpty()) {
				return 0;
			}
			return nets.stream()
					.filter(net -> net.groupIds.contains(groupId))
					.findFirst()
					.map(net -> netTimeline.getLast().getOrDefault(net.id, 0))
					.orElse(0);
		}

		public int signalForComponent(int componentIndex) {
			if (timeline.isEmpty()) {
				return 0;
			}
			return timeline.getLast().getOrDefault(componentIndex, 0);
		}

		public List<WireNet> unconnectedNets() {
			return nets.stream()
					.filter(net -> net.attachedPorts == 0 && !net.points.isEmpty())
					.toList();
		}
	}

	public static final class Node {
		public final int id;
		public final PlacedComponent component;
		private final Set<WireNet> inputNets = new LinkedHashSet<>();
		private final Set<WireNet> outputNets = new LinkedHashSet<>();

		private Node(int id, PlacedComponent component) {
			this.id = id;
			this.component = component;
		}
	}

	public static final class WireNet {
		public int id;
		private final Set<GridPoint> points = new LinkedHashSet<>();
		private final Set<Long> groupIds = new LinkedHashSet<>();
		private int attachedPorts;

		private WireNet(int id) {
			this.id = id;
		}
	}

	private static final class WireCell {
		private final Set<Long> groupIds = new LinkedHashSet<>();
	}

	private enum Direction {
		EAST(1, 0),
		SOUTH(0, 1),
		WEST(-1, 0),
		NORTH(0, -1);

		private final int deltaX;
		private final int deltaZ;

		Direction(int deltaX, int deltaZ) {
			this.deltaX = deltaX;
			this.deltaZ = deltaZ;
		}

		private GridPoint offset(GridPoint point) {
			return new GridPoint(point.x() + deltaX, point.z() + deltaZ);
		}

		private Direction opposite() {
			return switch (this) {
				case EAST -> WEST;
				case SOUTH -> NORTH;
				case WEST -> EAST;
				case NORTH -> SOUTH;
			};
		}

		private static Direction fromRotation(int rotation) {
			return switch (Math.floorMod(rotation, 4)) {
				case 1 -> SOUTH;
				case 2 -> WEST;
				case 3 -> NORTH;
				default -> EAST;
			};
		}
	}

	private record GridPoint(int x, int z) {
		private List<GridPoint> orthogonalNeighbors() {
			return List.of(
					new GridPoint(x + 1, z),
					new GridPoint(x - 1, z),
					new GridPoint(x, z + 1),
					new GridPoint(x, z - 1)
			);
		}
	}
}
