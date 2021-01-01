package org.firstinspires.ftc.teamcode.opmodes.test;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;

import org.firstinspires.ftc.teamcode.hardware.Drivetrain;
import org.firstinspires.ftc.teamcode.hardware.Robot;

@TeleOp(name="Where Am I")
public class WhereAmI extends OpMode
{
    private Drivetrain drivetrain;
    
    @Override
    public void init()
    {
        drivetrain = new Robot(hardwareMap).drivetrain;
        drivetrain.top_right.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        drivetrain.top_left.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        drivetrain.bottom_left.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        drivetrain.bottom_right.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
    }
    
    @Override
    public void loop()
    {
        telemetry.addData("Front L", drivetrain.top_left.getCurrentPosition());
        telemetry.addData("Front R", drivetrain.top_right.getCurrentPosition());
        telemetry.addData("Back L", drivetrain.bottom_left.getCurrentPosition());
        telemetry.addData("Back R", drivetrain.bottom_right.getCurrentPosition());
    }
}
