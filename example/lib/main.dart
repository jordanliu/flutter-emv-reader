import 'package:emv_card_reader/card.dart';
import 'package:emv_card_reader/emv_card_reader.dart';
import 'package:flutter/material.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class ScannedCard {
  final EmvCard card;
  final DateTime timestamp;

  ScannedCard(this.card, this.timestamp);
}

class _MyAppState extends State<MyApp> {
  // Card reader instance
  final _emv = EmvCardReader();

  // Card list
  final List<ScannedCard> _cards = [];

  @override
  void initState() {
    super.initState();

    final ac = (bool status) {
      // Availability status
      if (!status) {
        return;
      }

      // Stream NFC tags
      _emv.stream().listen((card) {
        if (card != null) {
          final now = DateTime.now();
          // Debounce: Check if the same card was scanned recently (e.g., < 1.5 seconds)
          if (_cards.isNotEmpty) {
            final lastCard = _cards.first;
            final isSameNumber = lastCard.card.number == card.number;
            final isRecent =
                now.difference(lastCard.timestamp).inMilliseconds < 1500;

            if (isSameNumber && isRecent) {
              return;
            }
          }

          setState(() {
            // Add new card to the top of the list
            _cards.insert(0, ScannedCard(card, now));
          });
        }
      });
    };

    final sc = (_) {
      // Check availability
      _emv.available().then(ac);
    };

    // Start NFC adapter
    _emv.start().then(sc);
  }

  @override
  void dispose() {
    // Stop NFC adapter
    _emv.stop();

    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('flutter-emv-reader'),
          actions: [
            IconButton(
              icon: Icon(Icons.delete),
              onPressed: () => setState(() => _cards.clear()),
            )
          ],
        ),
        body: _cards.isEmpty
            ? Center(child: Text('Waiting for card...'))
            : ListView.builder(
                itemCount: _cards.length,
                itemBuilder: (context, index) {
                  final scannedCard = _cards[index];
                  final card = scannedCard.card;
                  final number = card.number;
                  final type = card.type;
                  final holder = card.holder;
                  final expire = card.expire;
                  final status = card.status;

                  // Calculate the sequential ID (Newest = highest number)
                  final id = _cards.length - index;

                  return ListTile(
                    leading: CircleAvatar(
                      child: Text('$id'),
                    ),
                    title: Text(number ?? 'Unknown Number'),
                    subtitle: Text('$type - $holder - $expire'),
                    trailing: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(status ?? 'Unknown'),
                        Text(
                          '${scannedCard.timestamp.hour}:${scannedCard.timestamp.minute}:${scannedCard.timestamp.second}',
                          style: TextStyle(fontSize: 10, color: Colors.grey),
                        ),
                      ],
                    ),
                  );
                },
              ),
      ),
    );
  }
}
