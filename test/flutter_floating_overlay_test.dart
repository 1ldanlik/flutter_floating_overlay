import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_floating_overlay/flutter_floating_overlay.dart';
import 'package:flutter_floating_overlay/flutter_floating_overlay_platform_interface.dart';
import 'package:flutter_floating_overlay/flutter_floating_overlay_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFlutterFloatingOverlayPlatform
    with MockPlatformInterfaceMixin
    implements FlutterFloatingOverlayPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final FlutterFloatingOverlayPlatform initialPlatform = FlutterFloatingOverlayPlatform.instance;

  test('$MethodChannelFlutterFloatingOverlay is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFlutterFloatingOverlay>());
  });

  test('getPlatformVersion', () async {
    FlutterFloatingOverlay flutterFloatingOverlayPlugin = FlutterFloatingOverlay();
    MockFlutterFloatingOverlayPlatform fakePlatform = MockFlutterFloatingOverlayPlatform();
    FlutterFloatingOverlayPlatform.instance = fakePlatform;

    expect(await flutterFloatingOverlayPlugin.getPlatformVersion(), '42');
  });
}
