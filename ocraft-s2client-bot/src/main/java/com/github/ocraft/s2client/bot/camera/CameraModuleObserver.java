package com.github.ocraft.s2client.bot.camera;
import SC2APIProtocol.Sc2Api;
import com.github.ocraft.s2client.bot.S2ReplayObserver;
import com.github.ocraft.s2client.protocol.request.Requests;
import com.github.ocraft.s2client.protocol.spatial.Point2d;
import com.github.ocraft.s2client.protocol.spatial.PointI;

public class CameraModuleObserver extends CameraModule {

    public CameraModuleObserver(S2ReplayObserver bot) {
        super(bot);
    }

    @Override
    public void onStart() {

        // This should work once it is implemented on the proto side. Once i actually figure it out...
        //Requests.observerActions(); // It has somthing to do with this one i believe

        //m_client().control().replayControl().proto().sendRequest(request);

        /*Sc2Api.RequestObserverAction
        Sc2Api.ActionObserverPlayerPerspective.Builder request = Sc2Api.ActionObserverPlayerPerspective.newBuilder();
        request.setPlayerId(0); // 0 = everyone
        m_client().control().replayControl().proto().sendRequest(request);

        sc2::GameRequestPtr request = m_observer->Control()->Proto().MakeRequest();
        SC2APIProtocol::RequestObserverAction* obsRequest = request->mutable_obs_action();
        SC2APIProtocol::ObserverAction* action = obsRequest->add_actions();
        SC2APIProtocol::ActionObserverPlayerPerspective * player_perspective = action->mutable_player_perspective();
        player_perspective->set_player_id(0);  // 0 = everyone
        m_client->Control()->Proto().SendRequest(request);
        m_client->Control()->WaitForResponse();*/
        m_client.control().observerAction().cameraSetPerspective(0);
        super.onStart();
    }

    @Override
    public void onFrame() {
        super.onFrame();
    }

    @Override
    protected void updateCameraPositionExcecute() {
        if (followUnit) {
            //log.info("Moving Camera to Unit");

            // Annoyingly this doesn't appear to work.... the other one does though for some reason
            // m_client().control().observerAction().cameraFollowUnits(cameraFocusUnit.unit());

            m_client.control().observerAction().cameraMove(cameraFocusUnit.unit().getPosition().toPoint2d(), cameraDistance);
        } else {
            //log.info("Moving Camera to Position {} {}", cameraFocusPosition.getX(), cameraFocusPosition.getY());

            m_client.control().observerAction().cameraMove(Point2d.of(cameraFocusPosition.getX(), cameraFocusPosition.getY()), cameraDistance);
        }
    }
}