from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]/'app/src/main/java/com/vlesscardvpn'
class AutoEnergy(unittest.TestCase):
 def test_auto_never_selects_fast_and_manual_wins(self):
  p=(ROOT/'core/AutoEnergyPolicy.kt').read_text()
  self.assertIn('if (profile != AUTO) return Pick(profile',p)
  self.assertNotIn('Pick("fast"',p)
 def test_pressure_battery_and_screen_pick_eco(self):
  p=(ROOT/'core/AutoEnergyPolicy.kt').read_text()
  self.assertIn('i.battery in 1..30',p);self.assertIn('if (!i.screen) return Pick("eco"',p)
  self.assertIn('i.gaming || i.voiceCall || i.lowMemory || i.predictedPressure',p)
 def test_phone_power_wires_auto_and_pace(self):
  a=(ROOT/'core/PhonePower.kt').read_text()
  self.assertIn('AutoEnergyPolicy.resolve(s.powerProfile, input, drain)',a)
  self.assertIn('PowerPolicy.Options(effective,',a);self.assertIn('it.copy(loopMs',a)
 def test_settings_accept_auto_and_keep_default(self):
  self.assertIn('listOf("auto", "balanced", "eco", "fast")',(ROOT/'model/Settings.kt').read_text())
 def test_probe_interval_follows_the_effective_profile(self):
  self.assertIn('"auto" -> interval(AutoEnergyPolicy.effective.value)',(ROOT/'core/BatteryProbePolicy.kt').read_text())
 def test_chips_offer_auto(self):
  self.assertIn('"auto" to "Авто"',(ROOT/'ui/SettingsScreen.kt').read_text())
  self.assertIn('"auto" to "Авто"',(ROOT/'ui/DeviceScreen.kt').read_text())
 def test_watson_shows_temperature_and_drain(self):
  w=(ROOT/'ui/WatsonScreen.kt').read_text()
  self.assertIn('yTemp',w);self.assertIn('AutoEnergyPolicy.drain.collectAsState()',w)
 def test_auto_energy_never_rewrites_settings(self):
  self.assertNotIn('Store.update',(ROOT/'core/AutoEnergyPolicy.kt').read_text())
 def test_device_efficiency_preset_has_auto(self):
  self.assertIn('"auto" -> s.copy(powerProfile = "auto")',(ROOT/'core/DeviceEfficiency.kt').read_text())
