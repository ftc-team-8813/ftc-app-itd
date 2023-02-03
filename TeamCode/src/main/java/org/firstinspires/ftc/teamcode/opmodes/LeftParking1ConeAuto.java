package org.firstinspires.ftc.teamcode.opmodes;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.teamcode.hardware.Arm;
import org.firstinspires.ftc.teamcode.hardware.Drivetrain;
import org.firstinspires.ftc.teamcode.hardware.Horizontal;
import org.firstinspires.ftc.teamcode.hardware.Intake;
import org.firstinspires.ftc.teamcode.hardware.Lift;
import org.firstinspires.ftc.teamcode.hardware.Robot;
import org.firstinspires.ftc.teamcode.hardware.navigation.Odometry;
import org.firstinspires.ftc.teamcode.hardware.navigation.PID;
import org.firstinspires.ftc.teamcode.opmodes.util.FTCDVS;
import org.firstinspires.ftc.teamcode.util.Logger;
import org.firstinspires.ftc.teamcode.util.LoopTimer;
import org.firstinspires.ftc.teamcode.vision.AprilTagDetectionPipeline;
import org.openftc.apriltag.AprilTagDetection;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;

import java.util.ArrayList;

@Autonomous(name = "!!Left Parking 1 Cone Auto!!")
public class LeftParking1ConeAuto extends LoggingOpMode{

    private Lift lift;
    private Horizontal horizontal;
    private Arm arm;
    private Intake intake;
    private Drivetrain drivetrain;
    private Odometry odometry;

    private String result = "Nothing";

    private int main_id = 0;
    private int arm_id = 0;

    private OpenCvCamera camera;
    private AprilTagDetectionPipeline aprilTagDetectionPipeline;

    private static final double FEET_PER_METER = 3.28084;

    private double fx = 578.272;
    private double fy = 578.272;
    private double cx = 402.145;
    private double cy = 221.506;

    private double tagsize = 0.166;

    private final PID arm_PID = new PID(0.009, 0, 0, 0.1, 0, 0);
    private final PID horizontal_PID = new PID(0.01, 0, 0, 0, 0, 0);
    private final PID lift_PID = new PID(0.02, 0, 0, 0.015, 0, 0);

    private ElapsedTime timer = new ElapsedTime();

    private ElapsedTime lift_trapezoid = new ElapsedTime();;
    private double lift_accel = 0.27;

    private double lift_target = 0;
    private double horizontal_target = 0;
    private double arm_target = 0;

    private ElapsedTime liftTimer = new ElapsedTime();
    private boolean liftTimerReset = false;

    private final Logger log = new Logger("Cone Auto");

    private double timer_point_1;
    private double timer_point_2;
    private double timer_point_3;
    private double timer_point_4;
    private double timer_point_5;
    private double timer_point_6;

    private double lift_power;
    private double horizontal_power;
    private double arm_power;

    @Override
    public void init() {
        super.init();
        Robot robot = Robot.initialize(hardwareMap);
        lift = robot.lift;
        horizontal = robot.horizontal;
        arm = robot.arm;
        intake = robot.intake;
        drivetrain = robot.drivetrain;
        odometry = robot.odometry;

        odometry.Down();
        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        camera = OpenCvCameraFactory.getInstance().createWebcam(hardwareMap.get(WebcamName.class, "Webcam 1"), cameraMonitorViewId);
        aprilTagDetectionPipeline = new AprilTagDetectionPipeline(tagsize, fx, fy, cx, cy);

        camera.setPipeline(aprilTagDetectionPipeline);
        camera.openCameraDeviceAsync(new OpenCvCamera.AsyncCameraOpenListener()
        {
            @Override
            public void onOpened()
            {
                camera.startStreaming(800,448, OpenCvCameraRotation.UPRIGHT);
            }

            @Override
            public void onError(int errorCode)
            {

            }
        });

//        telemetry.setMsTransmissionInterval(50);

        odometry.resetEncoders();
    }

    @Override
    public void init_loop() {
        super.init_loop();

        ArrayList<AprilTagDetection> currentDetections = aprilTagDetectionPipeline.getLatestDetections();

        if(currentDetections.size() != 0) {

            for (AprilTagDetection tag : currentDetections) {
                if (tag.id == 107) {
                    result = "FTC8813: 1";
                    break;
                }
                else if (tag.id == 350) {
                    result = "FTC8813: 2";
                    break;
                }
                else if (tag.id == 25) {
                    result = "FTC8813: 3";
                    break;
                }
                else {
                    result = "Nothing";
                }

            }
        }


        telemetry.addData("Detected", result);

        telemetry.update();

        if(!arm.getLimit()){
            arm.setPower(0.5);
        }
        if(!lift.getLimit()){
            lift.setPower(-0.2);
        }
        if(!horizontal.getLimit()){
            horizontal.setPower(0.3);
        }

        if(arm.getLimit()){
            arm.resetEncoders();
        }
        if(lift.getLimit()){
            lift.resetEncoders();
        }
        if(horizontal.getLimit()){
            horizontal.resetEncoders();
        }

        lift.setHolderPosition(0.3);

        arm.resetEncoders();
        lift.resetEncoders();
        horizontal.resetEncoders();
        odometry.resetEncoders();
    }


    @Override
    public void start() {
        super.start();
        lift_target = 745;
        lift_trapezoid.reset();
    }

    @Override
    public void loop() {

        odometry.updatePose();

//        timer_point_1 = LoopTimer.getLoopTime();

        switch (main_id) {
            case 0:
                drivetrain.autoMove(-6,-19,0,1,1,1, odometry.getPose(), telemetry);
                if (drivetrain.hasReached()) {
                    main_id += 1;
                    lift.setHolderPosition(0.39);
                }
                break;
            case 1:
                drivetrain.autoMove(-28.3,-23.1,46.34,1,1,1.6, odometry.getPose(), telemetry);
                if (drivetrain.hasReached()) {
                    main_id += 1;
                    timer.reset();
                }
                break;
            case 2:
                drivetrain.autoMove(-33,-24,46.34,0.65,0.65,3, odometry.getPose(), telemetry);
                if (drivetrain.hasReached() || timer.seconds() > 6) {
                    main_id += 1;
                    arm_target = -28;
                    lift_target = 0;
                }
                break;
            case 3:
                if (lift.getCurrentPosition() < 200) {
                    lift.setHolderPosition(0.14);
                    main_id += 1;
                }
                break;
            case 4:
                if (lift.getCurrentPosition() < 10) {
                    main_id += 1;
                }
                break;
            case 5:
                drivetrain.autoMove(-26,-18,0,1,1,1, odometry.getPose(),telemetry);
                if (drivetrain.hasReached()) {
                    main_id += 1;
                }
                break;
            case 6:
                switch (result) {
                    case "FTC8813: 1":
                        drivetrain.autoMove(-27,30,0,1,1,1, odometry.getPose(), telemetry);
                        if (drivetrain.hasReached()) {
                            main_id += 1;
                        }
                        break;
                    case "FTC8813: 3":
                        main_id += 1;
                        break;
                    default:
                        drivetrain.autoMove(-27,6,0,1,1,1, odometry.getPose(), telemetry);
                        if (drivetrain.hasReached()) {
                            main_id += 1;
                        }
                        break;
                }
                break;
            case 7:
                drivetrain.stop();
                break;
        }

//        timer_point_2 = LoopTimer.getLoopTime();

        lift_power = lift_PID.getOutPut(lift_target, lift.getCurrentPosition(), 1) * Math.min(lift_trapezoid.seconds() * lift_accel, 1); //change
        horizontal_power = horizontal_PID.getOutPut(horizontal_target,horizontal.getCurrentPosition(),0); //change
        arm_power = Range.clip(arm_PID.getOutPut(arm_target, arm.getCurrentPosition(), Math.cos(Math.toRadians(arm.getCurrentPosition() + 0))), -0.6, 1); //change

//        timer_point_3 = LoopTimer.getLoopTime();

        lift.setPower(lift_power);
        horizontal.setPower(horizontal_power);
        arm.setPower(arm_power);

//        timer_point_4 = LoopTimer.getLoopTime();

        drivetrain.update(odometry.getPose(), telemetry);

//        timer_point_5 = LoopTimer.getLoopTime();

        telemetry.addData("Main ID", main_id);
//        telemetry.addData("Distance", intake.getDistance());
//        telemetry.addData("Lift Power", lift_power);
//        telemetry.addData("Horizontal Power", horizontal_power);
//        telemetry.addData("Arm Power", arm_power);
//        telemetry.addData("Lift Target",lift_target);
//        telemetry.addData("Horizontal Target",horizontal_target);
//        telemetry.addData("Arm Target",arm_target);
//        telemetry.addData("Lift Position",lift.getCurrentPosition());
//        telemetry.addData("Horizontal Position",horizontal.getCurrentPosition());
//        telemetry.addData("Arm Position",arm.getCurrentPosition());
//        telemetry.addData("Timer Point 1", timer_point_1);
//        telemetry.addData("Timer Point 2", timer_point_2);
//        telemetry.addData("Timer Point 3", timer_point_3);
//        telemetry.addData("Timer Point 4", timer_point_4);
//        telemetry.addData("Timer Point 5", timer_point_5);
        telemetry.addData("Loop Time: ", LoopTimer.getLoopTime());
        telemetry.update();

        LoopTimer.resetTimer();
    }

    @Override
    public void stop() {
        super.stop();
    }

}