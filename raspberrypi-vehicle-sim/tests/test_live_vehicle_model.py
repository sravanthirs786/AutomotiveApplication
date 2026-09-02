import importlib.util
import pathlib
import sys
import time
import unittest


MODULE_PATH = pathlib.Path(__file__).parents[1] / "live-server" / "vehicle_sim.py"
SPEC = importlib.util.spec_from_file_location("live_vehicle_sim", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class LiveVehicleModelTests(unittest.TestCase):
    def test_body_state_is_plausible_while_cruising(self):
        state = MODULE.VehicleState(started_at=time.monotonic() - 30.0)
        state.update()
        self.assertGreater(state.speed, 1.0)
        self.assertFalse(state.front_left_door_open)
        self.assertTrue(state.locked)
        self.assertEqual(3, state.gear)

    def test_roadside_stop_opens_driver_door_and_sets_park(self):
        state = MODULE.VehicleState(started_at=time.monotonic() - 86.0)
        state.update()
        self.assertLess(state.speed, 1.0)
        self.assertTrue(state.front_left_door_open)
        self.assertTrue(state.parking_brake)
        self.assertEqual(0, state.gear)

    def test_lab_tpms_and_body_uds_payloads(self):
        simulator = MODULE.VehicleSimulator.__new__(MODULE.VehicleSimulator)
        simulator.state = MODULE.VehicleState()
        body = simulator.uds_response(0x7E3, bytes.fromhex("22D100"))
        pressure = simulator.uds_response(0x7E4, bytes.fromhex("22D200"))
        health = simulator.uds_response(0x7E4, bytes.fromhex("22D202"))
        self.assertEqual(bytes.fromhex("62D100C0"), body)
        self.assertEqual(bytes.fromhex("62D20000A800E800E600E5"), pressure)
        self.assertEqual(bytes.fromhex("62D2024E5B58560F"), health)


if __name__ == "__main__":
    unittest.main()
