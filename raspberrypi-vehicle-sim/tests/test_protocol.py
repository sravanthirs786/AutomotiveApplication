import unittest
from canio import Frame
from isotp import Reassembler


class ProtocolTests(unittest.TestCase):
    def test_linux_can_frame_wire_format(self):
        frame = Frame(0x7DF, bytes.fromhex("02010C"))
        packet = frame.pack()
        self.assertEqual(16, len(packet))
        self.assertEqual("df0700000300000002010c0000000000", packet.hex())
        self.assertEqual(frame, Frame.unpack(packet))

    def test_isotp_multiframe_vin(self):
        reassembler = Reassembler()
        payload, flow = reassembler.accept(Frame(0x7E8, bytes.fromhex("101462F1904C4142")))
        self.assertIsNone(payload)
        self.assertIsNotNone(flow)
        self.assertIsNone(reassembler.accept(Frame(0x7E8, bytes.fromhex("2153494D32365250")))[0])
        payload, _ = reassembler.accept(Frame(0x7E8, bytes.fromhex("2249343030303031")))
        self.assertEqual(bytes.fromhex("62F190") + b"LABSIM26RPI400001", payload)


if __name__ == "__main__":
    unittest.main()
