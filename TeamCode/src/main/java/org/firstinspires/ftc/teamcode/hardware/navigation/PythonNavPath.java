package org.firstinspires.ftc.teamcode.hardware.navigation;

import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.SparseArray;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.hardware.Robot;
import org.firstinspires.ftc.teamcode.util.Logger;
import org.firstinspires.ftc.teamcode.util.Scheduler;
import org.firstinspires.ftc.teamcode.util.event.Event;
import org.firstinspires.ftc.teamcode.util.event.EventBus;
import org.firstinspires.ftc.teamcode.util.python.Python;
import org.firstinspires.ftc.teamcode.util.websocket.Server;
import org.firstinspires.ftc.teamcode.util.websocket.UnixSocketServer;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;

public class PythonNavPath
{
    private Queue<NavCmdWrapper> commandQueue;
    private final Object cmdLock = new Object();
    private HashMap<String, NavPath.Actuator> actuators = new HashMap<>();
    private HashMap<String, Sensor> sensors = new HashMap<>();
    
    private SparseArray<Class<? extends NavCommand>> commandClasses;
    
    private EventBus eventBus;
    private Robot robot;
    private Navigator nav;
    private Python python;
    private Server pyServer;
    private String[] args;
    private File sockFile;
    
    private Logger log = new Logger("Python NavPath");
    
    public interface Sensor
    {
        public ByteBuffer getData();
    }
    
    public interface NavCommand
    {
        void recv(ByteBuffer payload);
        void run(Robot robot, EventBus bus, Navigator nav, CountDownLatch latch);
        void send(Server.Responder resp);
    }
    
    private static class NavCmdWrapper
    {
        NavCommand cmd;
        CountDownLatch counter;
        
        NavCmdWrapper(NavCommand cmd)
        {
            this.cmd = cmd;
            this.counter = new CountDownLatch(1);
        }
    }
    
    public PythonNavPath(String scriptFile, EventBus evBus, Robot robot, String... args)
    {
        commandQueue = new ArrayDeque<>();
        commandClasses = new SparseArray<>(256);
        this.eventBus = evBus;
        this.robot = robot;
        this.nav = new Navigator(robot.drivetrain, robot.drivetrain.getOdometry(), eventBus);
        
        this.python = new Python(scriptFile);
        this.args = args;
        
        sockFile = Python.getSocketFile();
        try
        {
            pyServer = new Server(new UnixSocketServer(sockFile.getPath()));
        } catch (IOException e)
        {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
    
    public Navigator getNavigator()
    {
        return nav;
    }
    
    public void start() throws IOException
    {
        registerProcessors();
        pyServer.startServer();
        
        String[] newArgs = new String[args.length + 1];
        newArgs[0] = sockFile.getPath();
        System.arraycopy(args, 0, newArgs, 1, args.length);
        
        python.start(newArgs);
    }
    
    public void update(Telemetry telemetry)
    {
        // event bus is not thread safe so we want to make sure that we subscribe things on the
        // main thread
        if (!commandQueue.isEmpty())
        {
            synchronized (cmdLock)
            {
                while (!commandQueue.isEmpty())
                {
                    NavCmdWrapper wrapper = commandQueue.remove();
                    wrapper.cmd.run(robot, eventBus, nav, wrapper.counter);
                }
            }
        }
        nav.update(telemetry);
    }
    
    public void stop()
    {
        pyServer.close();
        python.stop();
    }
    
    public void addProcessor(int id, Class<? extends NavCommand> cmdClass)
    {
        if (id < 1 || id > 253) throw new IllegalArgumentException("Invalid ID: " + id);
        commandClasses.append(id, cmdClass);
    }
    
    public void addActuator(String name, NavPath.Actuator actuator)
    {
        actuators.put(name, actuator);
    }
    
    public void addSensor(String name, Sensor sensor)
    {
        sensors.put(name, sensor);
    }
    
    private void registerProcessors()
    {
        addProcessor(0xFD, CmdWaitEvent.class);
        addProcessor(0xFC, CmdMove.class);
        addProcessor(0xFB, CmdTurn.class);
        addProcessor(0xFA, CmdActuator.class);
        addProcessor(0xF9, CmdSensor.class);
        for (int i = 0; i < 256; i++)
        {
            final Class<? extends NavCommand> command = commandClasses.get(i);
            pyServer.registerProcessor(i, (cmd, payload, resp) -> {
                try
                {
                    NavCommand instance = command.newInstance();
                    instance.recv(payload);
                    NavCmdWrapper wrapper = new NavCmdWrapper(instance);
                    synchronized (cmdLock)
                    {
                        commandQueue.add(wrapper);
                    }
                    wrapper.counter.await();
                    instance.send(resp);
                }
                catch (IllegalAccessException | InstantiationException e)
                {
                    log.e("Error creating %s", command.getSimpleName());
                    log.e("The class should not have a user-defined constructor.");
                    log.e(e);
                    resp.respond(ByteBuffer.wrap(new byte[] {(byte)0xFF}));
                }
                catch (InterruptedException e)
                {
                    log.w(e);
                    resp.respond(ByteBuffer.wrap(new byte[] {(byte)0xFE}));
                }
            });
        }
    }
    
    private class CmdSensor implements NavCommand
    {
        private String name;
        private ByteBuffer data;
        private int retval = 0;
        
        @Override
        public void recv(ByteBuffer payload)
        {
            byte[] data = new byte[payload.limit()];
            name = new String(data, StandardCharsets.UTF_8);
        }
    
        @Override
        public void run(Robot robot, EventBus bus, Navigator nav, CountDownLatch latch)
        {
            Sensor sensor = sensors.get(name);
            if (sensor == null) retval = 0x01;
            else data = sensor.getData();
            
            latch.countDown();
        }
    
        @Override
        public void send(Server.Responder resp)
        {
            if (data == null) resp.respond(ByteBuffer.wrap(new byte[] {(byte)retval}));
            else
            {
                byte[] ndata = new byte[data.limit() + 1];
                ndata[0] = (byte)retval;
                data.get(ndata, 1, data.limit());
                resp.respond(ByteBuffer.wrap(ndata));
            }
        }
    }
    
    private class CmdActuator implements NavCommand
    {
        private String name;
        private JsonObject params;
        private int retval = 0;
        
        @Override
        public void recv(ByteBuffer payload)
        {
            byte[] s = new byte[payload.limit()];
            payload.get(s);
            String combined = new String(s, StandardCharsets.UTF_8);
            String[] fields = combined.split("\0");
            if (fields.length != 2)
            {
                retval = 0x01;
                log.w("Bad data string: %d strings sent", fields.length);
            }
            else
            {
                name = fields[0];
                JsonParser parser = new JsonParser();
                try
                {
                    params = parser.parse(fields[1]).getAsJsonObject();
                }
                catch (JsonParseException | IllegalStateException e)
                {
                    retval = 0x01;
                    log.w("Bad JSON: %s", fields[1]);
                }
            }
        }
    
        @Override
        public void run(Robot robot, EventBus bus, Navigator nav, CountDownLatch latch)
        {
            if (retval != 0)
            {
                latch.countDown();
                return;
            }
            
            NavPath.Actuator actuator = actuators.get(name);
            if (actuator == null) retval = 0x02;
            else actuator.move(params);
            latch.countDown();
        }
    
        @Override
        public void send(Server.Responder resp)
        {
            resp.respond(ByteBuffer.wrap(new byte[] {(byte)retval}));
        }
    }
    
    private static class CmdTurn implements NavCommand
    {
        private double rotation;
        private double speed;
        private boolean absolute = false;
        
        @Override
        public void recv(ByteBuffer payload)
        {
            rotation = payload.getFloat();
            speed = payload.getFloat();
            byte flags = payload.get();
            absolute = (flags & 0x1) != 0;
        }
    
        @Override
        public void run(Robot robot, EventBus bus, Navigator nav, CountDownLatch latch)
        {
            nav.setTurnSpeed(speed);
            if (absolute) nav.turnAbs(rotation);
            else nav.turn(rotation);
            latch.countDown();
        }
    
        @Override
        public void send(Server.Responder resp)
        {
            resp.respond(ByteBuffer.wrap(new byte[] {0x00}));
        }
    }
    
    private static class CmdMove implements NavCommand
    {
        private double x;
        private double y;
        private double speed;
        private boolean absolute = false;
        private boolean reverse = false;
        
        @Override
        public void recv(ByteBuffer payload)
        {
            x = payload.getFloat();
            y = payload.getFloat();
            speed = payload.getFloat();
            byte flags = payload.get();
            absolute = (flags & 0x1) != 0;
            reverse  = (flags & 0x2) != 0;
        }
    
        @Override
        public void run(Robot robot, EventBus bus, Navigator nav, CountDownLatch latch)
        {
            nav.setForwardSpeed(speed);
            nav.setTurnSpeed(speed);
            double currX = nav.getTargetX();
            double currY = nav.getTargetY();
            
            if (absolute) nav.goTo(x, y, reverse);
            else nav.goTo(currX + x, currY + y, reverse);
            latch.countDown();
        }
    
        @Override
        public void send(Server.Responder resp)
        {
            resp.respond(ByteBuffer.wrap(new byte[] {(byte)0x00}));
        }
    }
    
    private static class CmdWaitEvent implements NavCommand
    {
        private int channel;
        private String evClass;
        private int retval;
        
        @Override
        public void recv(ByteBuffer payload)
        {
            channel = payload.getInt();
            byte[] data = new byte[payload.limit()];
            payload.get(data);
            evClass = new String(data, StandardCharsets.UTF_8);
            retval = 0x0;
        }
    
        @Override
        public void run(Robot robot, EventBus bus, Navigator nav, CountDownLatch latch)
        {
            try
            {
                Class<?> cls = Class.forName(evClass);
                Class<? extends Event> evClass = cls.asSubclass(Event.class);
                
                EventBus.Subscriber<? extends Event> sub =
                        new EventBus.Subscriber<>(evClass, (ev, _bus, _sub) -> {
                            latch.countDown();
                            _bus.unsubscribe(_sub);
                        }, "Python event waiter", channel);
                bus.subscribe(sub);
            }
            catch (ClassNotFoundException e)
            {
                retval = 0x01;
            }
            catch (ClassCastException e)
            {
                retval = 0x02;
            }
        }
    
        @Override
        public void send(Server.Responder resp)
        {
            resp.respond(ByteBuffer.wrap(new byte[] {(byte)retval}));
        }
    }
}