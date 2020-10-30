package io.iohk.ethereum.blockchain.sync

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import akka.util.ByteString
import io.iohk.ethereum.network.EtcPeerManagerActor.PeerInfo
import io.iohk.ethereum.network.Peer
import io.iohk.ethereum.network.p2p.messages.CommonMessages.Status64
import io.iohk.ethereum.network.p2p.messages.Versions
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class PeersClientSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {
  "PeerClient" should "determined the best peer based on it latest checkpoint number and total difficulty" in {
    import Peers._

    val table = Table[Map[Peer, PeerInfo], Option[Peer], String](
      ("PeerInfo map", "Expected best peer", "Scenario info (selected peer)"),
      (
        Map(),
        None,
        "No peers"
      ),
      (
        Map(peer1 -> peerInfo(0, 100, fork = false)),
        None,
        "Single peer"
      ),
      (
        Map(peer1 -> peerInfo(0, 100, fork = false), peer2 -> peerInfo(0, 50, fork = true)),
        Some(peer2),
        "Peer2 with lower TD but following the ETC fork"
      ),
      (
        Map(peer1 -> peerInfo(0, 100), peer2 -> peerInfo(0, 101)),
        Some(peer2),
        "Peer2 with higher TD"
      ),
      (
        Map(peer1 -> peerInfo(0, 100), peer2 -> peerInfo(0, 101), peer3 -> peerInfo(1, 50)),
        Some(peer3),
        "Peer3 with lower TD but higher checkpoint number"
      ),
      (
        Map(peer1 -> peerInfo(0, 100), peer2 -> peerInfo(4, 101), peer3 -> peerInfo(4, 50)),
        Some(peer2),
        "Peer2 with equal checkpoint number and higher TD"
      )
    )

    forAll(table) { (peerInfoMap, expectedPeer, _) =>
      PeersClient.bestPeer(peerInfoMap) shouldEqual expectedPeer
    }
  }

  object Peers {
    implicit val system = ActorSystem("PeersClient_System")

    val peer1 = Peer(new InetSocketAddress("127.0.0.1", 1), TestProbe().ref, false)
    val peer2 = Peer(new InetSocketAddress("127.0.0.1", 2), TestProbe().ref, false)
    val peer3 = Peer(new InetSocketAddress("127.0.0.1", 3), TestProbe().ref, false)

    private val peerStatus = Status64(
      protocolVersion = Versions.PV63,
      networkId = 1,
      totalDifficulty = 0,
      latestCheckpointNumber = 0,
      bestHash = ByteString.empty,
      genesisHash = ByteString.empty
    )

    def peerInfo(chkp: Int, td: Int, fork: Boolean = true): PeerInfo =
      PeerInfo(
        peerStatus.copy(latestCheckpointNumber = chkp, totalDifficulty = td),
        td,
        chkp,
        forkAccepted = fork,
        maxBlockNumber = 42,
        bestBlockHash = ByteString.empty
      )
  }
}