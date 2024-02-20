package org.cdm.logisim.uart;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceData;

import java.util.ArrayDeque;
import java.util.Queue;

public class UartData implements InstanceData {

    private final Queue<Integer> rxBuffer = new ArrayDeque<>();

    private Value lastClockState = Value.FALSE;

    public Queue<Integer> getRxBuffer() {
        return rxBuffer;
    }

    public ClockEvent checkClock(Value newClockState) {
        ClockEvent event = ClockEvent.NONE;

        if (lastClockState == Value.FALSE && newClockState == Value.TRUE) {
            event = ClockEvent.RISING_EDGE;
        }

        if (lastClockState == Value.TRUE && newClockState == Value.FALSE) {
            event = ClockEvent.FALLING_EDGE;
        }

        lastClockState = newClockState;

        return event;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException var2) {
            return null;
        }
    }
}
