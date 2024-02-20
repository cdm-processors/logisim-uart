package org.cdm.logisim.uart;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.util.StringGetter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class UartComponent extends InstanceFactory {

    private static final int DELAY = 1;

    private static final int MAX_RX_STRING_LENGTH = 20;
    private static final int MAX_STATUS_STRING_LENGTH = 22;

    private static final Attribute<Integer> PORT_ATTR =
            Attributes.forIntegerRange("Port", 0, 65535);

    private final Map<Integer, SocketThread> threads = new HashMap<>();

    public UartComponent() {
        super("UART");

        setOffsetBounds(Bounds.create(0, 0, 120, 60));

        Port[] ports = new Port[] {
                new Port(0, 20, Port.OUTPUT, 8), // RX
                new Port(0, 40, Port.INPUT, 8), // TX
                new Port(30, 60, Port.INPUT, 1), // CLK
                new Port(60, 60, Port.INPUT, 1), // READ
                new Port(90, 0, Port.OUTPUT, 1), // CONNECTED
                new Port(90, 60, Port.INPUT, 1), // EN
                new Port(30, 0, Port.OUTPUT, 1) // DATA_AVAILABLE
        };

        ports[Ports.RX].setToolTip(get("RX"));
        ports[Ports.TX].setToolTip(get("TX"));
        ports[Ports.CLK].setToolTip(get("clk"));
        ports[Ports.READ].setToolTip(get("read"));
        ports[Ports.CONNECTED].setToolTip(get("connected"));
        ports[Ports.EN].setToolTip(get("en"));
        ports[Ports.DATA_AVAILABLE].setToolTip(get("data_available"));

        setPorts(ports);

        setAttributes(
                new Attribute[]{ PORT_ATTR },
                new Object[]{ 7241 }
        );
    }

    public void propagate(InstanceState state) {
        UartData componentData = getComponentData(state);
        SocketThread socketThread = getSocketThread(state.getAttributeValue(PORT_ATTR));

        boolean read = state.getPort(Ports.READ) != Value.FALSE;
        boolean en = state.getPort(Ports.EN) != Value.FALSE;

        ClockEvent clockEvent = componentData.checkClock(state.getPort(Ports.CLK));

        if (clockEvent == ClockEvent.RISING_EDGE) {
            if (socketThread.connected()) {
                int b;
                while ((b = socketThread.read()) != -1) {
                    // System.out.println("Read " + b);
                    componentData.getRxBuffer().add(b);
                }
            }
        } else if (clockEvent == ClockEvent.FALLING_EDGE && en) {
            if (read) {
                componentData.getRxBuffer().poll();
            } else {
                int b = state.getPort(Ports.TX).toIntValue();

                if (socketThread.connected()) {
                    try {
                        // System.out.println("Write " + b);
                        socketThread.write(b);
                    } catch (IOException e) {
                        System.err.println(e);
                    }
                }
            }
        }

        if (componentData.getRxBuffer().size() > 0) {
            state.setPort(
                    Ports.RX,
                    Value.createKnown(BitWidth.create(8), componentData.getRxBuffer().peek()),
                    DELAY
            );
            state.setPort(Ports.DATA_AVAILABLE, Value.TRUE, DELAY);
        } else {
            state.setPort(
                    Ports.RX,
                    Value.createKnown(BitWidth.create(8), 0),
                    DELAY
            );
            state.setPort(Ports.DATA_AVAILABLE, Value.FALSE, DELAY);
        }

        if (socketThread.connected()) {
            state.setPort(Ports.CONNECTED, Value.TRUE, DELAY);
        } else {
            state.setPort(Ports.CONNECTED, Value.FALSE, DELAY);
        }
    }

    public void paintInstance(InstancePainter painter) {
        painter.drawBounds();

        painter.drawPort(Ports.RX);
        painter.drawPort(Ports.TX);
        painter.drawPort(Ports.CONNECTED);
        painter.drawPort(Ports.READ);
        painter.drawPort(Ports.EN);
        painter.drawPort(Ports.DATA_AVAILABLE);

        painter.drawClock(Ports.CLK, Direction.NORTH);

        painter.getGraphics().drawString(
                "en",
                painter.getBounds().getX() + 84, painter.getBounds().getY() + 57
        );

        painter.getGraphics().drawString(
                "rd/wr'",
                painter.getBounds().getX() + 49, painter.getBounds().getY() + 57
        );

        painter.getGraphics().drawString(
                "dt",
                painter.getBounds().getX() + 25, painter.getBounds().getY() + 11
        );

        painter.getGraphics().drawString(
                "con",
                painter.getBounds().getX() + 82, painter.getBounds().getY() + 11
        );

        StringBuilder rxBytesStringBuilder = new StringBuilder("RX: ");

        for (int b : ((UartData) painter.getData()).getRxBuffer()) {
            rxBytesStringBuilder.append(String.format(" %02x", b));
        }

        limitStringLength(rxBytesStringBuilder, MAX_RX_STRING_LENGTH);

        painter.getGraphics().drawString(
                rxBytesStringBuilder.toString(),
                painter.getBounds().getX() + 5, painter.getBounds().getY() + 25
        );

        SocketThread socketThread = getSocketThread(painter.getAttributeValue(PORT_ATTR));
        StringBuilder statusStringBuilder = new StringBuilder("Status: ");

        switch (socketThread.getStatus()) {
            case DOWN:
                statusStringBuilder.append("down");
                break;
            case WAITING:
                statusStringBuilder.append("waiting");
                break;
            case CONNECTING:
                statusStringBuilder.append("connecting");
                break;
            case CONNECTED:
                statusStringBuilder.append("connected");
                break;
            case DISCONNECTING:
                statusStringBuilder.append("disconnecting");
                break;
            case BIND_ERROR:
                statusStringBuilder.append("port is in use");
                break;
            case ERROR:
                statusStringBuilder.append("error");
                break;
        }

        limitStringLength(statusStringBuilder, MAX_STATUS_STRING_LENGTH);

        painter.getGraphics().drawString(
                statusStringBuilder.toString(),
                painter.getBounds().getX() + 5, painter.getBounds().getY() + 44
        );

        /*GraphicsUtil.drawText(
                painter.getGraphics(),
                rxBytesStringBuilder.toString(),
                painter.getBounds().getX() + 10, painter.getBounds().getY() + 10,
                0, 0
        );*/
    }

    @Override
    protected void configureNewInstance(Instance instance) {
        instance.addAttributeListener();
    }

    @Override
    protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
        int newPort = (int) instance.getAttributeValue(attr);

        if (!threads.containsKey(newPort)) {
            createSocketThread(newPort);
        }
    }

    private SocketThread createSocketThread(int port) {
        SocketThread socketThread = new SocketThread(port);
        socketThread.start();

        threads.put(port, socketThread);

        return socketThread;
    }

    private SocketThread getSocketThread(int port) {
        SocketThread socketThread;

        if (!threads.containsKey(port)) {
            socketThread = createSocketThread(port);
        } else {
            socketThread = threads.get(port);
        }

        return socketThread;
    }

    private UartData getComponentData(InstanceState state) {
        UartData componentData = (UartData) state.getData();

        if (componentData == null) {
            componentData = new UartData();
            state.setData(componentData);
        }

        return componentData;
    }

    private static void limitStringLength(StringBuilder builder, int length) {
        if (builder.length() > length) {
            builder.delete(length - 3, builder.length());
            builder.append("...");
        }
    }

    private static StringGetter get(String str) {
        return () -> str;
    }
}
