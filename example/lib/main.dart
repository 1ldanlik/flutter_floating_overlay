import 'dart:developer';

import 'package:flutter/material.dart';
import 'package:flutter_floating_overlay/flutter_floating_overlay.dart';
import 'package:flutter_floating_overlay_example/components/video_player_component.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MyApp());
}

@pragma("vm:entry-point")
void overlayMain() async {
  WidgetsFlutterBinding.ensureInitialized();

  final url = await FlutterFloatingOverlay.getVideoUrl() ?? '';

  runApp(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      home: VideoPlayerComponent(path: url),
      // home: Container(
      //   color: Colors.red,
      //   width: 100,
      //   height: 100,
      // ),
    ),
  );
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
          appBar: AppBar(
            title: const Text('Plugin example app'),
          ),
          body: Column(
            children: [
              TextButton(
                onPressed: () async {
                  final status =
                      await FlutterFloatingOverlay.isPermissionGranted();
                  log("Is Permission Granted: $status");
                },
                child: const Text("Check Permission"),
              ),
              const SizedBox(height: 10.0),
              TextButton(
                onPressed: () async {
                  final bool? res =
                      await FlutterFloatingOverlay.requestPermission();
                  log("status: $res");
                },
                child: const Text("Request Permission"),
              ),
              const SizedBox(height: 10.0),
              TextButton(
                onPressed: () async {
                  if (await FlutterFloatingOverlay.isActive()) {
                    print('fffffffffffff');
                    return;
                  }
                  await FlutterFloatingOverlay.showOverlay(
                    enableDrag: true,
                    overlayTitle: "X-SLAYER",
                    overlayContent: 'Overlay Enabled',
                    flag: OverlayFlag.defaultFlag,
                    visibility: NotificationVisibility.visibilityPublic,
                    positionGravity: PositionGravity.none,
                    height: 500,
                    width: 500,
                  );
                },
                child: const Text("Show Overlay"),
              ),
              const SizedBox(height: 10.0),
              TextButton(
                onPressed: () {
                  log('Try to close');
                  FlutterFloatingOverlay.closeOverlay()
                      .then((value) => log('STOPPED: alue: $value'));
                },
                child: const Text("Close Overlay"),
              ),
            ],
          )),
    );
  }
}
